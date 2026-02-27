package com.golf.tournament.service;

import com.golf.tournament.dto.leaderboard.LeaderboardEntryDTO;
import com.golf.tournament.dto.tournamentadmin.*;
import com.golf.tournament.exception.BadRequestException;
import com.golf.tournament.exception.ResourceNotFoundException;
import com.golf.tournament.model.*;
import com.golf.tournament.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TournamentAdminStageService {

    private final TournamentAdminRepository tournamentAdminRepository;
    private final TournamentAdminStageRepository stageRepository;
    private final TournamentAdminStageScoreRepository stageScoreRepository;
    private final TournamentRepository tournamentRepository;
    private final TournamentInscriptionRepository inscriptionRepository;
    private final FrutalesScoreRepository frutalesScoreRepository;
    private final LeaderboardService leaderboardService;

    @Transactional(readOnly = true)
    public List<TournamentAdminStageDTO> getStages(Long tournamentAdminId) {
        ensureTournamentAdminExists(tournamentAdminId);
        return stageRepository.findByTournamentAdminIdOrderByCreatedAtDesc(tournamentAdminId).stream()
                .map(this::toStageDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TournamentAdminStageDTO getStage(Long tournamentAdminId, Long stageId) {
        TournamentAdminStage stage = getStageOrThrow(tournamentAdminId, stageId);
        return toStageDTO(stage);
    }

    @Transactional(readOnly = true)
    public List<TournamentRelationOptionDTO> getStageRelationOptions(Long tournamentAdminId, Long stageId) {
        TournamentAdmin admin = tournamentAdminRepository.findById(tournamentAdminId)
                .orElseThrow(() -> new ResourceNotFoundException("TournamentAdmin", "id", tournamentAdminId));

        final Set<Long> currentIds;
        if (stageId != null) {
            TournamentAdminStage stage = getStageOrThrow(tournamentAdminId, stageId);
            currentIds = stage.getTournaments().stream().map(Tournament::getId).collect(Collectors.toSet());
        } else {
            currentIds = new HashSet<>();
        }

        Set<Long> usedByOtherStages = stageRepository.findUsedTournamentIdsByAdminExcludingStage(
                tournamentAdminId,
                stageId
        );

        return admin.getTournaments().stream()
                .sorted(Comparator.comparing(Tournament::getFechaInicio))
                .filter(t -> currentIds.contains(t.getId()) || !usedByOtherStages.contains(t.getId()))
                .map(t -> TournamentRelationOptionDTO.builder()
                        .id(t.getId())
                        .nombre(t.getNombre())
                        .fechaInicio(t.getFechaInicio())
                        .related(currentIds.contains(t.getId()))
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public TournamentAdminStageDTO createStage(Long tournamentAdminId, CreateTournamentAdminStageRequest request) {
        TournamentAdmin admin = tournamentAdminRepository.findById(tournamentAdminId)
                .orElseThrow(() -> new ResourceNotFoundException("TournamentAdmin", "id", tournamentAdminId));

        List<Tournament> tournaments = resolveStageTournaments(admin, request.getTournamentIds(), null);

        TournamentAdminStage stage = TournamentAdminStage.builder()
                .tournamentAdmin(admin)
                .nombre(request.getNombre())
                .tournaments(tournaments)
                .build();

        stage = stageRepository.save(stage);
        log.info("Etapa creada: {} para torneo admin {}", stage.getId(), tournamentAdminId);
        return toStageDTO(stage);
    }

    @Transactional
    public TournamentAdminStageDTO updateStage(Long tournamentAdminId, Long stageId, UpdateTournamentAdminStageRequest request) {
        TournamentAdminStage stage = getStageOrThrow(tournamentAdminId, stageId);
        List<Tournament> tournaments = resolveStageTournaments(stage.getTournamentAdmin(), request.getTournamentIds(), stageId);

        stage.setNombre(request.getNombre());
        stage.setTournaments(tournaments);
        stage = stageRepository.save(stage);

        log.info("Etapa actualizada: {} para torneo admin {}", stage.getId(), tournamentAdminId);
        return toStageDTO(stage);
    }

    @Transactional(readOnly = true)
    public TournamentAdminStageBoardDTO getStageBoard(Long tournamentAdminId, Long stageId) {
        TournamentAdminStage stage = getStageOrThrow(tournamentAdminId, stageId);
        List<Tournament> stageTournaments = getSortedStageTournaments(stage);

        Map<Long, Player> playersById = collectPlayersById(stageTournaments);
        Map<Long, Map<Long, Integer>> pointsByTournamentAndPlayer = collectPointsByTournamentAndPlayer(stageTournaments);
        Map<Long, TournamentAdminStageScore> persistedScores = stageScoreRepository.findByStageIdOrderByPositionAsc(stageId).stream()
                .collect(Collectors.toMap(s -> s.getPlayer().getId(), Function.identity()));

        List<TournamentAdminStageBoardDTO.PlayerStageRowDTO> rows = playersById.values().stream()
                .map(player -> {
                    Map<Long, Integer> pointsByTournament = new LinkedHashMap<>();
                    for (Tournament tournament : stageTournaments) {
                        int pts = pointsByTournamentAndPlayer
                                .getOrDefault(tournament.getId(), Collections.emptyMap())
                                .getOrDefault(player.getId(), 0);
                        pointsByTournament.put(tournament.getId(), pts);
                    }

                    TournamentAdminStageScore score = persistedScores.get(player.getId());

                    return TournamentAdminStageBoardDTO.PlayerStageRowDTO.builder()
                            .playerId(player.getId())
                            .playerName(player.getApellido() + " " + player.getNombre())
                            .handicapIndex(player.getHandicapIndex())
                            .totalPoints(score != null ? score.getTotalPoints() : 0)
                            .position(score != null ? score.getPosition() : null)
                            .pointsByTournament(pointsByTournament)
                            .build();
                })
                .sorted(Comparator.comparing(
                        TournamentAdminStageBoardDTO.PlayerStageRowDTO::getPosition,
                        Comparator.nullsLast(Integer::compareTo)
                ).thenComparing(TournamentAdminStageBoardDTO.PlayerStageRowDTO::getPlayerName))
                .collect(Collectors.toList());

        return TournamentAdminStageBoardDTO.builder()
                .stageId(stage.getId())
                .tournamentAdminId(tournamentAdminId)
                .stageName(stage.getNombre())
                .stageCreatedAt(stage.getCreatedAt())
                .tournaments(stageTournaments.stream()
                        .map(t -> TournamentAdminStageBoardDTO.TournamentDateColumnDTO.builder()
                                .tournamentId(t.getId())
                                .tournamentName(t.getNombre())
                                .fechaInicio(t.getFechaInicio())
                                .doublePoints(t.getDoublePoints())
                                .build())
                        .collect(Collectors.toList()))
                .rows(rows)
                .build();
    }

    @Transactional
    public TournamentAdminStageBoardDTO calculateStageScores(Long tournamentAdminId, Long stageId) {
        TournamentAdminStage stage = getStageOrThrow(tournamentAdminId, stageId);
        List<Tournament> stageTournaments = getSortedStageTournaments(stage);
        if (stageTournaments.isEmpty()) {
            throw new BadRequestException("La etapa no tiene fechas para calcular");
        }

        Map<Long, Player> playersById = collectPlayersById(stageTournaments);
        Map<Long, Map<Long, Integer>> pointsByTournamentAndPlayer = collectPointsByTournamentAndPlayer(stageTournaments);

        Tournament lastTournament = stageTournaments.get(stageTournaments.size() - 1);
        Map<Long, BigDecimal> lastTournamentNetoByPlayer = leaderboardService
                .getLeaderboard(lastTournament.getId(), null)
                .stream()
                .filter(entry -> entry.getScoreNeto() != null)
                .collect(Collectors.toMap(
                        LeaderboardEntryDTO::getPlayerId,
                        e -> e.getScoreNeto(),
                        (a, b) -> a
                ));

        List<TournamentAdminStageScore> calculated = playersById.values().stream()
                .map(player -> {
                    int total = 0;
                    for (Tournament tournament : stageTournaments) {
                        total += pointsByTournamentAndPlayer
                                .getOrDefault(tournament.getId(), Collections.emptyMap())
                                .getOrDefault(player.getId(), 0);
                    }

                    return TournamentAdminStageScore.builder()
                            .stage(stage)
                            .player(player)
                            .totalPoints(total)
                            .tieBreakHandicapIndex(player.getHandicapIndex())
                            .lastTournamentScoreNeto(lastTournamentNetoByPlayer.get(player.getId()))
                            .build();
                })
                .sorted(buildStageRankingComparator())
                .collect(Collectors.toList());

        for (int i = 0; i < calculated.size(); i++) {
            calculated.get(i).setPosition(i + 1);
        }

        stageScoreRepository.deleteByStageId(stageId);
        stageScoreRepository.saveAll(calculated);

        log.info("Etapa {} recalculada. Jugadores: {}", stageId, calculated.size());
        return getStageBoard(tournamentAdminId, stageId);
    }

    private Comparator<TournamentAdminStageScore> buildStageRankingComparator() {
        return Comparator.comparing(TournamentAdminStageScore::getTotalPoints, Comparator.reverseOrder())
                .thenComparing(TournamentAdminStageScore::getTieBreakHandicapIndex, Comparator.nullsLast(BigDecimal::compareTo))
                .thenComparing(TournamentAdminStageScore::getLastTournamentScoreNeto, Comparator.nullsLast(BigDecimal::compareTo))
                .thenComparing(s -> s.getPlayer().getApellido() + " " + s.getPlayer().getNombre());
    }

    private Map<Long, Player> collectPlayersById(List<Tournament> tournaments) {
        Map<Long, Player> playersById = new LinkedHashMap<>();
        for (Tournament tournament : tournaments) {
            List<TournamentInscription> inscriptions = inscriptionRepository.findByTournamentId(tournament.getId());
            for (TournamentInscription inscription : inscriptions) {
                playersById.put(inscription.getPlayer().getId(), inscription.getPlayer());
            }
        }
        return playersById;
    }

    private Map<Long, Map<Long, Integer>> collectPointsByTournamentAndPlayer(List<Tournament> tournaments) {
        Map<Long, Map<Long, Integer>> pointsByTournamentAndPlayer = new HashMap<>();
        for (Tournament tournament : tournaments) {
            Map<Long, Integer> byPlayer = frutalesScoreRepository.findByTournamentIdOrderByTotalPointsDesc(tournament.getId()).stream()
                    .collect(Collectors.toMap(
                            fs -> fs.getPlayer().getId(),
                            FrutalesScore::getTotalPoints,
                            (a, b) -> a
                    ));
            pointsByTournamentAndPlayer.put(tournament.getId(), byPlayer);
        }
        return pointsByTournamentAndPlayer;
    }

    private TournamentAdminStageDTO toStageDTO(TournamentAdminStage stage) {
        List<Tournament> stageTournaments = getSortedStageTournaments(stage);
        return TournamentAdminStageDTO.builder()
                .id(stage.getId())
                .tournamentAdminId(stage.getTournamentAdmin().getId())
                .nombre(stage.getNombre())
                .fechasCount(stageTournaments.size())
                .createdAt(stage.getCreatedAt())
                .tournamentIds(stageTournaments.stream().map(Tournament::getId).collect(Collectors.toList()))
                .tournaments(stageTournaments.stream()
                        .map(t -> TournamentAdminStageDTO.TournamentDateDTO.builder()
                                .id(t.getId())
                                .nombre(t.getNombre())
                                .fechaInicio(t.getFechaInicio())
                                .doublePoints(t.getDoublePoints())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }

    private List<Tournament> getSortedStageTournaments(TournamentAdminStage stage) {
        return stage.getTournaments().stream()
                .sorted(Comparator.comparing(Tournament::getFechaInicio))
                .collect(Collectors.toList());
    }

    private List<Tournament> resolveStageTournaments(TournamentAdmin admin, List<Long> tournamentIds, Long stageId) {
        if (tournamentIds == null || tournamentIds.isEmpty()) {
            throw new BadRequestException("Debe seleccionar al menos una fecha");
        }

        Set<Long> uniqueIds = new HashSet<>(tournamentIds);
        Set<Long> adminTournamentIds = admin.getTournaments().stream()
                .map(Tournament::getId)
                .collect(Collectors.toSet());

        if (!adminTournamentIds.containsAll(uniqueIds)) {
            throw new BadRequestException("Todas las fechas seleccionadas deben pertenecer al torneo administrativo");
        }

        Set<Long> usedByOtherStages = stageRepository.findUsedTournamentIdsByAdminExcludingStage(admin.getId(), stageId);
        Set<Long> conflicts = new HashSet<>(uniqueIds);
        conflicts.retainAll(usedByOtherStages);
        if (!conflicts.isEmpty()) {
            throw new BadRequestException("Hay fechas seleccionadas ya relacionadas a otra etapa");
        }

        List<Tournament> tournaments = tournamentRepository.findAllById(uniqueIds);
        if (tournaments.size() != uniqueIds.size()) {
            throw new BadRequestException("Una o más fechas seleccionadas no existen");
        }

        boolean anyNonFrutales = tournaments.stream().anyMatch(t -> !"FRUTALES".equals(t.getTipo()));
        if (anyNonFrutales) {
            throw new BadRequestException("Solo se pueden relacionar torneos de tipo FRUTALES");
        }

        return tournaments;
    }

    private TournamentAdminStage getStageOrThrow(Long tournamentAdminId, Long stageId) {
        return stageRepository.findByIdAndTournamentAdminId(stageId, tournamentAdminId)
                .orElseThrow(() -> new ResourceNotFoundException("TournamentAdminStage", "id", stageId));
    }

    private void ensureTournamentAdminExists(Long tournamentAdminId) {
        if (!tournamentAdminRepository.existsById(tournamentAdminId)) {
            throw new ResourceNotFoundException("TournamentAdmin", "id", tournamentAdminId);
        }
    }
}
