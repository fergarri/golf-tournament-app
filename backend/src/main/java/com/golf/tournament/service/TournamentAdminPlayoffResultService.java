package com.golf.tournament.service;

import com.golf.tournament.dto.tournamentadmin.TournamentAdminPlayoffResultsDTO;
import com.golf.tournament.exception.ResourceNotFoundException;
import com.golf.tournament.model.Player;
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

    @Transactional(readOnly = true)
    public TournamentAdminPlayoffResultsDTO getResults(Long tournamentAdminId) {
        ensureTournamentAdminExists(tournamentAdminId);
        List<TournamentAdminStage> stages = stageRepository.findByTournamentAdminIdOrderByCreatedAtAsc(tournamentAdminId);
        List<TournamentAdminPlayoffResult> persisted = playoffResultRepository
                .findByTournamentAdminIdOrderByPositionAsc(tournamentAdminId);

        if (persisted.isEmpty()) {
            return TournamentAdminPlayoffResultsDTO.builder()
                    .tournamentAdminId(tournamentAdminId)
                    .stages(toStageColumns(stages))
                    .rows(Collections.emptyList())
                    .build();
        }

        Map<Long, Map<Long, TournamentAdminStageScore>> stageScoresByStageAndPlayer = loadStageScores(stages);

        List<TournamentAdminPlayoffResultsDTO.RowDTO> rows = persisted.stream()
                .map(result -> {
                    Map<Long, Integer> pointsByStage = new LinkedHashMap<>();
                    for (TournamentAdminStage stage : stages) {
                        int points = stageScoresByStageAndPlayer
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

        return TournamentAdminPlayoffResultsDTO.builder()
                .tournamentAdminId(tournamentAdminId)
                .stages(toStageColumns(stages))
                .rows(rows)
                .build();
    }

    @Transactional
    public TournamentAdminPlayoffResultsDTO calculateResults(Long tournamentAdminId) {
        ensureTournamentAdminExists(tournamentAdminId);
        List<TournamentAdminStage> stages = stageRepository.findByTournamentAdminIdOrderByCreatedAtAsc(tournamentAdminId);

        if (stages.isEmpty()) {
            playoffResultRepository.deleteByTournamentAdminId(tournamentAdminId);
            return TournamentAdminPlayoffResultsDTO.builder()
                    .tournamentAdminId(tournamentAdminId)
                    .stages(Collections.emptyList())
                    .rows(Collections.emptyList())
                    .build();
        }

        // Always recalculate all stages before calculating playoff results.
        for (TournamentAdminStage stage : stages) {
            stageService.calculateStageScores(tournamentAdminId, stage.getId());
        }

        Map<Long, Map<Long, TournamentAdminStageScore>> stageScoresByStageAndPlayer = loadStageScores(stages);
        List<Long> stageIdsDescendingForTieBreak = stages.stream()
                .map(TournamentAdminStage::getId)
                .collect(Collectors.toList());
        Collections.reverse(stageIdsDescendingForTieBreak);

        Map<Long, Candidate> candidatesByPlayer = new HashMap<>();
        for (TournamentAdminStage stage : stages) {
            for (TournamentAdminStageScore score : stageScoresByStageAndPlayer.getOrDefault(stage.getId(), Collections.emptyMap()).values()) {
                Candidate candidate = candidatesByPlayer.computeIfAbsent(
                        score.getPlayer().getId(),
                        k -> new Candidate(score.getPlayer())
                );
                candidate.totalPoints += score.getTotalPoints() != null ? score.getTotalPoints() : 0;
                candidate.positionsByStageId.put(stage.getId(), score.getPosition());
            }
        }

        List<Candidate> ordered = new ArrayList<>(candidatesByPlayer.values());
        ordered.sort(buildPlayoffComparator(stageIdsDescendingForTieBreak));

        List<TournamentAdminPlayoffResult> resultsToPersist = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            Candidate candidate = ordered.get(i);
            int rankingPosition = i + 1;

            resultsToPersist.add(TournamentAdminPlayoffResult.builder()
                    .tournamentAdmin(tournamentAdminRepository.getReferenceById(tournamentAdminId))
                    .player(candidate.player)
                    .totalPoints(candidate.totalPoints)
                    .position(rankingPosition)
                    .qualified(rankingPosition <= 8)
                    .build());
        }

        playoffResultRepository.deleteByTournamentAdminId(tournamentAdminId);
        playoffResultRepository.saveAll(resultsToPersist);

        return getResults(tournamentAdminId);
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

    private Map<Long, Map<Long, TournamentAdminStageScore>> loadStageScores(List<TournamentAdminStage> stages) {
        Map<Long, Map<Long, TournamentAdminStageScore>> map = new LinkedHashMap<>();
        for (TournamentAdminStage stage : stages) {
            Map<Long, TournamentAdminStageScore> byPlayer = stageScoreRepository.findByStageIdOrderByPositionAsc(stage.getId())
                    .stream()
                    .collect(Collectors.toMap(s -> s.getPlayer().getId(), Function.identity(), (a, b) -> a));
            map.put(stage.getId(), byPlayer);
        }
        return map;
    }

    private Comparator<Candidate> buildPlayoffComparator(List<Long> stageIdsDescendingForTieBreak) {
        return (a, b) -> {
            int totalPointsCompare = Integer.compare(b.totalPoints, a.totalPoints);
            if (totalPointsCompare != 0) {
                return totalPointsCompare;
            }

            BigDecimal aHcp = a.player.getHandicapIndex();
            BigDecimal bHcp = b.player.getHandicapIndex();
            int hcpCompare = Comparator.nullsLast(BigDecimal::compareTo).compare(aHcp, bHcp);
            if (hcpCompare != 0) {
                return hcpCompare;
            }

            for (Long stageId : stageIdsDescendingForTieBreak) {
                Integer aPos = a.positionsByStageId.get(stageId);
                Integer bPos = b.positionsByStageId.get(stageId);
                int stagePosCompare = compareStagePosition(aPos, bPos);
                if (stagePosCompare != 0) {
                    return stagePosCompare;
                }
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

    private void ensureTournamentAdminExists(Long tournamentAdminId) {
        if (!tournamentAdminRepository.existsById(tournamentAdminId)) {
            throw new ResourceNotFoundException("TournamentAdmin", "id", tournamentAdminId);
        }
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
