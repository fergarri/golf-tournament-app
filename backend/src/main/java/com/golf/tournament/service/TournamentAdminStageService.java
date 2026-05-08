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
    private final TournamentScoreRepository tournamentScoreRepository;
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

        // Buscar todos los torneos del tipo correcto que no estén en ninguna otra etapa de ningún admin
        List<Tournament> available = stageId != null
                ? tournamentRepository.findAvailableForStageByTipoExcludingStage(admin.getTipo(), stageId)
                : tournamentRepository.findAvailableForStageByTipo(admin.getTipo());

        return available.stream()
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
        String tipo = stage.getTournamentAdmin().getTipo();
        List<Tournament> stageTournaments = getSortedStageTournaments(stage);

        String hcpScoreType = "FRUTALES".equals(tipo) ? TournamentScore.SCORE_TYPE_GLOBAL : TournamentScore.SCORE_TYPE_CATEGORY;

        Map<Long, Player> playersById = collectPlayersById(stageTournaments);
        Map<Long, Map<Long, Integer>> hcpPoints = collectPointsByTournamentAndPlayer(stageTournaments, hcpScoreType);
        Map<Long, TournamentAdminStageScore> hcpScores = stageScoreRepository
                .findByStageIdAndScoreTypeOrderByPositionAsc(stageId, "HCP").stream()
                .collect(Collectors.toMap(s -> s.getPlayer().getId(), Function.identity()));

        List<TournamentAdminStageBoardDTO.TournamentDateColumnDTO> tournamentColumns = stageTournaments.stream()
                .map(t -> TournamentAdminStageBoardDTO.TournamentDateColumnDTO.builder()
                        .tournamentId(t.getId())
                        .tournamentName(t.getNombre())
                        .fechaInicio(t.getFechaInicio())
                        .doublePoints(t.getDoublePoints())
                        .build())
                .collect(Collectors.toList());

        List<TournamentAdminStageBoardDTO.PlayerStageRowDTO> hcpRows = buildBoardRows(
                playersById, stageTournaments, hcpPoints, hcpScores);

        List<TournamentAdminStageBoardDTO.PlayerStageRowDTO> scratchRows = null;
        List<TournamentAdminStageBoardDTO.CategoryRowsDTO> categoryRows = null;
        if ("CLASICO".equals(tipo)) {
            Map<Long, Map<Long, Integer>> scratchPoints = collectPointsByTournamentAndPlayer(
                    stageTournaments, TournamentScore.SCORE_TYPE_SCRATCH);
            Map<Long, TournamentAdminStageScore> scratchScores = stageScoreRepository
                    .findByStageIdAndScoreTypeOrderByPositionAsc(stageId, "SCRATCH").stream()
                    .collect(Collectors.toMap(s -> s.getPlayer().getId(), Function.identity()));
            scratchRows = buildBoardRows(playersById, stageTournaments, scratchPoints, scratchScores);

            // Filas por categoría: el jugador aparece en la categoría de su última inscripción en la etapa
            // Sus puntos totales = suma de TODOS los puntos CATEGORY acumulados (arrastra puntos aunque haya cambiado de categoría)
            Map<Long, TournamentCategory> playerLatestCategory = buildPlayerLatestCategoryMap(stageTournaments);
            List<TournamentCategory> uniqueCategories = playerLatestCategory.values().stream()
                    .collect(Collectors.toMap(TournamentCategory::getId, c -> c, (a, b) -> a))
                    .values().stream()
                    .sorted(Comparator.comparing(TournamentCategory::getHandicapMin))
                    .collect(Collectors.toList());
            categoryRows = uniqueCategories.stream()
                    .map(category -> {
                        Set<Long> playerIds = playerLatestCategory.entrySet().stream()
                                .filter(e -> e.getValue().getId().equals(category.getId()))
                                .map(Map.Entry::getKey)
                                .collect(Collectors.toSet());
                        List<TournamentAdminStageBoardDTO.PlayerStageRowDTO> catRows =
                                buildCategoryBoardRows(playerIds, playersById, stageTournaments, hcpPoints);
                        return TournamentAdminStageBoardDTO.CategoryRowsDTO.builder()
                                .categoryId(category.getId())
                                .categoryName(category.getNombre())
                                .handicapMin(category.getHandicapMin())
                                .handicapMax(category.getHandicapMax())
                                .rows(catRows)
                                .build();
                    })
                    .collect(Collectors.toList());
        }

        return TournamentAdminStageBoardDTO.builder()
                .stageId(stage.getId())
                .tournamentAdminId(tournamentAdminId)
                .stageName(stage.getNombre())
                .stageCreatedAt(stage.getCreatedAt())
                .tipo(tipo)
                .tournaments(tournamentColumns)
                .rows(hcpRows)
                .scratchRows(scratchRows)
                .categoryRows(categoryRows)
                .build();
    }

    private List<TournamentAdminStageBoardDTO.PlayerStageRowDTO> buildBoardRows(
            Map<Long, Player> playersById,
            List<Tournament> stageTournaments,
            Map<Long, Map<Long, Integer>> pointsByTournamentAndPlayer,
            Map<Long, TournamentAdminStageScore> persistedScores) {
        return playersById.values().stream()
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
    }

    @Transactional
    public TournamentAdminStageBoardDTO calculateStageScores(Long tournamentAdminId, Long stageId) {
        TournamentAdminStage stage = getStageOrThrow(tournamentAdminId, stageId);
        String tipo = stage.getTournamentAdmin().getTipo();
        List<Tournament> stageTournaments = getSortedStageTournaments(stage);
        if (stageTournaments.isEmpty()) {
            throw new BadRequestException("La etapa no tiene fechas para calcular");
        }

        Map<Long, Player> playersById = collectPlayersById(stageTournaments);

        Tournament lastTournament = stageTournaments.get(stageTournaments.size() - 1);
        Map<Long, BigDecimal> lastNetoByPlayer = leaderboardService
                .getLeaderboard(lastTournament.getId(), null)
                .stream()
                .filter(entry -> entry.getScoreNeto() != null)
                .collect(Collectors.toMap(LeaderboardEntryDTO::getPlayerId, e -> e.getScoreNeto(), (a, b) -> a));

        // ── HCP scores ──────────────────────────────────────────────────────────
        String hcpScoreType = "FRUTALES".equals(tipo) ? TournamentScore.SCORE_TYPE_GLOBAL : TournamentScore.SCORE_TYPE_CATEGORY;
        Map<Long, Map<Long, Integer>> hcpPoints = collectPointsByTournamentAndPlayer(stageTournaments, hcpScoreType);

        List<TournamentAdminStageScore> hcpCalculated = playersById.values().stream()
                .map(player -> {
                    int total = stageTournaments.stream()
                            .mapToInt(t -> hcpPoints.getOrDefault(t.getId(), Collections.emptyMap()).getOrDefault(player.getId(), 0))
                            .sum();
                    return TournamentAdminStageScore.builder()
                            .stage(stage)
                            .player(player)
                            .scoreType("HCP")
                            .totalPoints(total)
                            .tieBreakHandicapIndex(player.getHandicapIndex())
                            .lastTournamentScoreNeto(lastNetoByPlayer.get(player.getId()))
                            .build();
                })
                .sorted(buildStageRankingComparator())
                .collect(Collectors.toList());
        for (int i = 0; i < hcpCalculated.size(); i++) {
            hcpCalculated.get(i).setPosition(i + 1);
        }

        stageScoreRepository.deleteByStageIdAndScoreType(stageId, "HCP");
        stageScoreRepository.saveAll(hcpCalculated);

        // ── SCRATCH scores (solo para CLASICO) ───────────────────────────────────
        if ("CLASICO".equals(tipo)) {
            Map<Long, Map<Long, Integer>> scratchPoints = collectPointsByTournamentAndPlayer(
                    stageTournaments, TournamentScore.SCORE_TYPE_SCRATCH);

            List<TournamentAdminStageScore> scratchCalculated = playersById.values().stream()
                    .map(player -> {
                        int total = stageTournaments.stream()
                                .mapToInt(t -> scratchPoints.getOrDefault(t.getId(), Collections.emptyMap()).getOrDefault(player.getId(), 0))
                                .sum();
                        return TournamentAdminStageScore.builder()
                                .stage(stage)
                                .player(player)
                                .scoreType("SCRATCH")
                                .totalPoints(total)
                                .tieBreakHandicapIndex(player.getHandicapIndex())
                                .lastTournamentScoreNeto(lastNetoByPlayer.get(player.getId()))
                                .build();
                    })
                    .sorted(buildStageRankingComparator())
                    .collect(Collectors.toList());
            for (int i = 0; i < scratchCalculated.size(); i++) {
                scratchCalculated.get(i).setPosition(i + 1);
            }

            stageScoreRepository.deleteByStageIdAndScoreType(stageId, "SCRATCH");
            stageScoreRepository.saveAll(scratchCalculated);
        }

        log.info("Etapa {} recalculada (tipo={}). Jugadores: {}", stageId, tipo, playersById.size());
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

    private Map<Long, Map<Long, Integer>> collectPointsByTournamentAndPlayer(List<Tournament> tournaments, String scoreType) {
        Map<Long, Map<Long, Integer>> pointsByTournamentAndPlayer = new HashMap<>();
        for (Tournament tournament : tournaments) {
            Map<Long, Integer> byPlayer = tournamentScoreRepository
                    .findByTournamentIdAndScoreTypeOrderByTotalPointsDesc(tournament.getId(), scoreType)
                    .stream()
                    .collect(Collectors.toMap(
                            ts -> ts.getPlayer().getId(),
                            TournamentScore::getTotalPoints,
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

        List<Tournament> tournaments = tournamentRepository.findAllById(uniqueIds);
        if (tournaments.size() != uniqueIds.size()) {
            throw new BadRequestException("Una o más fechas seleccionadas no existen");
        }

        // Validar que todos los torneos sean del tipo del admin
        boolean anyWrongTipo = tournaments.stream().anyMatch(t -> !admin.getTipo().equals(t.getTipo()));
        if (anyWrongTipo) {
            throw new BadRequestException("Solo se pueden relacionar torneos de tipo " + admin.getTipo());
        }

        // Validar que no estén ya usados en otra etapa de cualquier admin
        Set<Long> usedByOtherStages = stageRepository.findUsedTournamentIdsByAdminExcludingStage(admin.getId(), stageId);
        Set<Long> conflicts = new HashSet<>(uniqueIds);
        conflicts.retainAll(usedByOtherStages);
        if (!conflicts.isEmpty()) {
            throw new BadRequestException("Hay fechas seleccionadas ya relacionadas a otra etapa");
        }

        return tournaments;
    }

    /**
     * Para cada jugador inscripto en los torneos de la etapa, retorna la categoría de su ÚLTIMA inscripción.
     * Si un jugador cambió de categoría durante la etapa, arrastra todos los puntos acumulados y aparece en la última categoría.
     */
    private Map<Long, TournamentCategory> buildPlayerLatestCategoryMap(List<Tournament> sortedTournaments) {
        Map<Long, TournamentCategory> playerLatestCategory = new LinkedHashMap<>();
        for (Tournament tournament : sortedTournaments) {
            List<TournamentInscription> inscriptions = inscriptionRepository.findByTournamentId(tournament.getId());
            for (TournamentInscription inscription : inscriptions) {
                if (inscription.getCategory() != null) {
                    playerLatestCategory.put(inscription.getPlayer().getId(), inscription.getCategory());
                }
            }
        }
        return playerLatestCategory;
    }

    /**
     * Construye filas por categoría, calculando posiciones en base a los puntos acumulados de esa categoría.
     * Usa todos los puntos HCP del jugador (independientemente del category_id, para soportar cambios de categoría).
     */
    private List<TournamentAdminStageBoardDTO.PlayerStageRowDTO> buildCategoryBoardRows(
            Set<Long> playerIdsInCategory,
            Map<Long, Player> playersById,
            List<Tournament> stageTournaments,
            Map<Long, Map<Long, Integer>> hcpPoints) {

        List<TournamentAdminStageBoardDTO.PlayerStageRowDTO> rows = playersById.values().stream()
                .filter(p -> playerIdsInCategory.contains(p.getId()))
                .map(player -> {
                    Map<Long, Integer> pointsByTournament = new LinkedHashMap<>();
                    int total = 0;
                    for (Tournament t : stageTournaments) {
                        int pts = hcpPoints.getOrDefault(t.getId(), Collections.emptyMap())
                                .getOrDefault(player.getId(), 0);
                        pointsByTournament.put(t.getId(), pts);
                        total += pts;
                    }
                    return TournamentAdminStageBoardDTO.PlayerStageRowDTO.builder()
                            .playerId(player.getId())
                            .playerName(player.getApellido() + " " + player.getNombre())
                            .handicapIndex(player.getHandicapIndex())
                            .totalPoints(total)
                            .pointsByTournament(pointsByTournament)
                            .build();
                })
                .sorted(Comparator.comparing(TournamentAdminStageBoardDTO.PlayerStageRowDTO::getTotalPoints,
                        Comparator.reverseOrder())
                        .thenComparing(TournamentAdminStageBoardDTO.PlayerStageRowDTO::getPlayerName))
                .collect(Collectors.toList());

        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).setPosition(i + 1);
        }
        return rows;
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
