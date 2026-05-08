package com.golf.tournament.service;

import com.golf.tournament.dto.tournamentadmin.ScoringConfigDTO;
import com.golf.tournament.dto.tournamentadmin.TournamentAdminPlayoffResultsDTO;
import com.golf.tournament.exception.ResourceNotFoundException;
import com.golf.tournament.model.Player;
import com.golf.tournament.model.TournamentAdmin;
import com.golf.tournament.model.TournamentAdminPlayoffResult;
import com.golf.tournament.model.TournamentAdminStage;
import com.golf.tournament.model.TournamentAdminStageScore;
import com.golf.tournament.repository.TournamentAdminPlayoffResultRepository;
import com.golf.tournament.repository.TournamentAdminRepository;
import com.golf.tournament.repository.TournamentAdminStageRepository;
import com.golf.tournament.repository.TournamentAdminStageScoreRepository;
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
public class TournamentAdminPlayoffResultService {

    private final TournamentAdminRepository tournamentAdminRepository;
    private final TournamentAdminStageRepository stageRepository;
    private final TournamentAdminStageScoreRepository stageScoreRepository;
    private final TournamentAdminPlayoffResultRepository playoffResultRepository;
    private final TournamentAdminStageService stageService;
    private final TournamentAdminScoringConfigService scoringConfigService;

    @Transactional(readOnly = true)
    public TournamentAdminPlayoffResultsDTO getResults(Long tournamentAdminId) {
        TournamentAdmin admin = tournamentAdminRepository.findById(tournamentAdminId)
                .orElseThrow(() -> new ResourceNotFoundException("TournamentAdmin", "id", tournamentAdminId));
        String tipo = admin.getTipo();

        List<TournamentAdminStage> stages = stageRepository.findByTournamentAdminIdOrderByCreatedAtAsc(tournamentAdminId);
        List<TournamentAdminStage> stagesAsc = new ArrayList<>(stages);

        List<TournamentAdminPlayoffResult> hcpPersisted = playoffResultRepository
                .findByTournamentAdminIdAndScoreTypeOrderByPositionAsc(tournamentAdminId, "HCP");

        if (hcpPersisted.isEmpty()) {
            return TournamentAdminPlayoffResultsDTO.builder()
                    .tournamentAdminId(tournamentAdminId)
                    .tipo(tipo)
                    .stages(toStageColumns(stagesAsc))
                    .rows(Collections.emptyList())
                    .scratchRows("CLASICO".equals(tipo) ? Collections.emptyList() : null)
                    .build();
        }

        Map<Long, Map<Long, TournamentAdminStageScore>> hcpStageScores = loadStageScores(stagesAsc, "HCP");

        List<TournamentAdminPlayoffResultsDTO.RowDTO> hcpRows = buildPlayoffRows(hcpPersisted, stagesAsc, hcpStageScores);

        List<TournamentAdminPlayoffResultsDTO.RowDTO> scratchRows = null;
        if ("CLASICO".equals(tipo)) {
            List<TournamentAdminPlayoffResult> scratchPersisted = playoffResultRepository
                    .findByTournamentAdminIdAndScoreTypeOrderByPositionAsc(tournamentAdminId, "SCRATCH");
            Map<Long, Map<Long, TournamentAdminStageScore>> scratchStageScores = loadStageScores(stagesAsc, "SCRATCH");
            scratchRows = buildPlayoffRows(scratchPersisted, stagesAsc, scratchStageScores);
        }

        return TournamentAdminPlayoffResultsDTO.builder()
                .tournamentAdminId(tournamentAdminId)
                .tipo(tipo)
                .stages(toStageColumns(stagesAsc))
                .rows(hcpRows)
                .scratchRows(scratchRows)
                .build();
    }

    @Transactional
    public TournamentAdminPlayoffResultsDTO calculateResults(Long tournamentAdminId) {
        TournamentAdmin admin = tournamentAdminRepository.findById(tournamentAdminId)
                .orElseThrow(() -> new ResourceNotFoundException("TournamentAdmin", "id", tournamentAdminId));
        String tipo = admin.getTipo();

        List<TournamentAdminStage> stages = stageRepository.findByTournamentAdminIdOrderByCreatedAtAsc(tournamentAdminId);
        List<TournamentAdminStage> stagesAsc = new ArrayList<>(stages);

        if (stagesAsc.isEmpty()) {
            playoffResultRepository.deleteByTournamentAdminId(tournamentAdminId);
            return TournamentAdminPlayoffResultsDTO.builder()
                    .tournamentAdminId(tournamentAdminId)
                    .tipo(tipo)
                    .stages(Collections.emptyList())
                    .rows(Collections.emptyList())
                    .scratchRows("CLASICO".equals(tipo) ? Collections.emptyList() : null)
                    .build();
        }

        // Recalcular todas las etapas antes del playoff
        for (TournamentAdminStage stage : stagesAsc) {
            stageService.calculateStageScores(tournamentAdminId, stage.getId());
        }

        ScoringConfigDTO config = scoringConfigService.getOrDefaultByTournamentAdminId(tournamentAdminId);
        int qualifiedPositions = config.getQualifiedPlayoffPositions();

        List<Long> stageIdsDescForTieBreak = stagesAsc.stream()
                .map(TournamentAdminStage::getId)
                .collect(Collectors.toList());
        Collections.reverse(stageIdsDescForTieBreak);

        // ── HCP playoff ────────────────────────────────────────────────────────
        Map<Long, Map<Long, TournamentAdminStageScore>> hcpStageScores = loadStageScores(stagesAsc, "HCP");
        List<TournamentAdminPlayoffResult> hcpResults = buildPlayoffResults(
                tournamentAdminId, stagesAsc, hcpStageScores, stageIdsDescForTieBreak, qualifiedPositions, "HCP");

        playoffResultRepository.deleteByTournamentAdminIdAndScoreType(tournamentAdminId, "HCP");
        playoffResultRepository.saveAll(hcpResults);

        // ── SCRATCH playoff (solo CLASICO) ─────────────────────────────────────
        if ("CLASICO".equals(tipo)) {
            Map<Long, Map<Long, TournamentAdminStageScore>> scratchStageScores = loadStageScores(stagesAsc, "SCRATCH");
            List<TournamentAdminPlayoffResult> scratchResults = buildPlayoffResults(
                    tournamentAdminId, stagesAsc, scratchStageScores, stageIdsDescForTieBreak, qualifiedPositions, "SCRATCH");

            playoffResultRepository.deleteByTournamentAdminIdAndScoreType(tournamentAdminId, "SCRATCH");
            playoffResultRepository.saveAll(scratchResults);
        }

        return getResults(tournamentAdminId);
    }

    private List<TournamentAdminPlayoffResult> buildPlayoffResults(
            Long tournamentAdminId,
            List<TournamentAdminStage> stages,
            Map<Long, Map<Long, TournamentAdminStageScore>> stageScoresByStage,
            List<Long> stageIdsDescForTieBreak,
            int qualifiedPositions,
            String scoreType) {

        Map<Long, Candidate> candidatesByPlayer = new HashMap<>();
        for (TournamentAdminStage stage : stages) {
            for (TournamentAdminStageScore score : stageScoresByStage.getOrDefault(stage.getId(), Collections.emptyMap()).values()) {
                Candidate candidate = candidatesByPlayer.computeIfAbsent(
                        score.getPlayer().getId(), k -> new Candidate(score.getPlayer()));
                candidate.totalPoints += score.getTotalPoints() != null ? score.getTotalPoints() : 0;
                candidate.positionsByStageId.put(stage.getId(), score.getPosition());
            }
        }

        List<Candidate> ordered = new ArrayList<>(candidatesByPlayer.values());
        ordered.sort(buildPlayoffComparator(stageIdsDescForTieBreak));

        List<TournamentAdminPlayoffResult> results = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            Candidate candidate = ordered.get(i);
            int rankingPosition = i + 1;
            results.add(TournamentAdminPlayoffResult.builder()
                    .tournamentAdmin(tournamentAdminRepository.getReferenceById(tournamentAdminId))
                    .player(candidate.player)
                    .scoreType(scoreType)
                    .totalPoints(candidate.totalPoints)
                    .position(rankingPosition)
                    .qualified(rankingPosition <= qualifiedPositions)
                    .build());
        }
        return results;
    }

    private List<TournamentAdminPlayoffResultsDTO.RowDTO> buildPlayoffRows(
            List<TournamentAdminPlayoffResult> persisted,
            List<TournamentAdminStage> stages,
            Map<Long, Map<Long, TournamentAdminStageScore>> stageScores) {
        return persisted.stream()
                .map(result -> {
                    Map<Long, Integer> pointsByStage = new LinkedHashMap<>();
                    for (TournamentAdminStage stage : stages) {
                        int points = stageScores
                                .getOrDefault(stage.getId(), Collections.emptyMap())
                                .getOrDefault(result.getPlayer().getId(), emptyStageScore())
                                .getTotalPoints();
                        pointsByStage.put(stage.getId(), points);
                    }
                    Player player = result.getPlayer();
                    return TournamentAdminPlayoffResultsDTO.RowDTO.builder()
                            .playerId(player.getId())
                            .playerName(player.getApellido() + " " + player.getNombre())
                            .pointsByStage(pointsByStage)
                            .totalPoints(result.getTotalPoints())
                            .position(result.getPosition())
                            .qualified(result.getQualified())
                            .build();
                })
                .collect(Collectors.toList());
    }

    private List<TournamentAdminPlayoffResultsDTO.StageColumnDTO> toStageColumns(List<TournamentAdminStage> stages) {
        List<TournamentAdminPlayoffResultsDTO.StageColumnDTO> columns = new ArrayList<>();
        for (int i = 0; i < stages.size(); i++) {
            TournamentAdminStage stage = stages.get(i);
            columns.add(TournamentAdminPlayoffResultsDTO.StageColumnDTO.builder()
                    .stageId(stage.getId())
                    .code("E" + (i + 1))
                    .stageName(stage.getNombre())
                    .stageCreatedAt(stage.getCreatedAt())
                    .build());
        }
        return columns;
    }

    private Map<Long, Map<Long, TournamentAdminStageScore>> loadStageScores(
            List<TournamentAdminStage> stages, String scoreType) {
        Map<Long, Map<Long, TournamentAdminStageScore>> map = new LinkedHashMap<>();
        for (TournamentAdminStage stage : stages) {
            Map<Long, TournamentAdminStageScore> byPlayer = stageScoreRepository
                    .findByStageIdAndScoreTypeOrderByPositionAsc(stage.getId(), scoreType)
                    .stream()
                    .collect(Collectors.toMap(s -> s.getPlayer().getId(), Function.identity(), (a, b) -> a));
            map.put(stage.getId(), byPlayer);
        }
        return map;
    }

    private Comparator<Candidate> buildPlayoffComparator(List<Long> stageIdsDescendingForTieBreak) {
        return (a, b) -> {
            int totalPointsCompare = Integer.compare(b.totalPoints, a.totalPoints);
            if (totalPointsCompare != 0) return totalPointsCompare;

            BigDecimal aHcp = a.player.getHandicapIndex();
            BigDecimal bHcp = b.player.getHandicapIndex();
            int hcpCompare = Comparator.nullsLast(BigDecimal::compareTo).compare(aHcp, bHcp);
            if (hcpCompare != 0) return hcpCompare;

            for (Long stageId : stageIdsDescendingForTieBreak) {
                Integer aPos = a.positionsByStageId.get(stageId);
                Integer bPos = b.positionsByStageId.get(stageId);
                int stagePosCompare = compareStagePosition(aPos, bPos);
                if (stagePosCompare != 0) return stagePosCompare;
            }

            String aName = a.player.getApellido() + " " + a.player.getNombre();
            String bName = b.player.getApellido() + " " + b.player.getNombre();
            return aName.compareToIgnoreCase(bName);
        };
    }

    private int compareStagePosition(Integer aPos, Integer bPos) {
        int aValue = aPos == null ? Integer.MAX_VALUE : aPos;
        int bValue = bPos == null ? Integer.MAX_VALUE : bPos;
        return Integer.compare(aValue, bValue);
    }

    private TournamentAdminStageScore emptyStageScore() {
        return TournamentAdminStageScore.builder().totalPoints(0).build();
    }

    private static class Candidate {
        private final Player player;
        private int totalPoints;
        private final Map<Long, Integer> positionsByStageId = new HashMap<>();

        private Candidate(Player player) {
            this.player = player;
            this.totalPoints = 0;
        }
    }
}
