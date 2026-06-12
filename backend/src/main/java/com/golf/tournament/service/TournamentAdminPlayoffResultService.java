package com.golf.tournament.service;

import com.golf.tournament.dto.tournamentadmin.ScoringConfigDTO;
import com.golf.tournament.dto.tournamentadmin.TournamentAdminPlayoffResultsDTO;
import com.golf.tournament.exception.ResourceNotFoundException;
import com.golf.tournament.model.Player;
import com.golf.tournament.model.Tournament;
import com.golf.tournament.model.TournamentAdmin;
import com.golf.tournament.model.TournamentAdminPlayoffResult;
import com.golf.tournament.model.TournamentAdminStage;
import com.golf.tournament.model.TournamentAdminStageScore;
import com.golf.tournament.model.TournamentCategory;
import com.golf.tournament.repository.TournamentAdminPlayoffResultRepository;
import com.golf.tournament.repository.TournamentAdminRepository;
import com.golf.tournament.repository.TournamentAdminStageRepository;
import com.golf.tournament.repository.TournamentAdminStageScoreRepository;
import com.golf.tournament.repository.TournamentCategoryRepository;
import com.golf.tournament.repository.TournamentRepository;
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
    private final TournamentRepository tournamentRepository;
    private final TournamentCategoryRepository categoryRepository;

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
            List<TournamentAdminPlayoffResultsDTO.RowDTO> emptyScratch = null;
            if ("CLASICO".equals(tipo)) {
                ScoringConfigDTO configCheck = scoringConfigService.getOrDefaultByTournamentAdminId(tournamentAdminId);
                int scratchCount = configCheck.getQualifiedPlayoffPositionsScratch() != null
                        ? configCheck.getQualifiedPlayoffPositionsScratch() : 0;
                if (scratchCount > 0) {
                    emptyScratch = Collections.emptyList();
                }
            }
            return TournamentAdminPlayoffResultsDTO.builder()
                    .tournamentAdminId(tournamentAdminId)
                    .tipo(tipo)
                    .stages(toStageColumns(stagesAsc))
                    .rows(Collections.emptyList())
                    .scratchRows(emptyScratch)
                    .build();
        }

        Map<Long, Map<Long, TournamentAdminStageScore>> hcpStageScores = loadStageScores(stagesAsc, "HCP");

        // Leyenda de categorías (solo CLASICO + PER_CATEGORY)
        List<TournamentAdminPlayoffResultsDTO.CategoryLegendDTO> categoryLegend = null;
        if ("CLASICO".equals(tipo)) {
            ScoringConfigDTO config = scoringConfigService.getOrDefaultByTournamentAdminId(tournamentAdminId);
            if ("PER_CATEGORY".equals(config.getHcpQualifiedMode()) && !stagesAsc.isEmpty()) {
                categoryLegend = buildCategoryLegend(stagesAsc);
            }
        }

        List<TournamentAdminPlayoffResultsDTO.RowDTO> hcpRows = buildPlayoffRows(hcpPersisted, stagesAsc, hcpStageScores);

        List<TournamentAdminPlayoffResultsDTO.RowDTO> scratchRows = null;
        if ("CLASICO".equals(tipo)) {
            ScoringConfigDTO configForScratch = scoringConfigService.getOrDefaultByTournamentAdminId(tournamentAdminId);
            int qualifiedPositionsScratch = configForScratch.getQualifiedPlayoffPositionsScratch() != null
                    ? configForScratch.getQualifiedPlayoffPositionsScratch() : 0;
            if (qualifiedPositionsScratch > 0) {
                List<TournamentAdminPlayoffResult> scratchPersisted = playoffResultRepository
                        .findByTournamentAdminIdAndScoreTypeOrderByPositionAsc(tournamentAdminId, "SCRATCH");
                Map<Long, Map<Long, TournamentAdminStageScore>> scratchStageScores = loadStageScores(stagesAsc, "SCRATCH");
                scratchRows = buildPlayoffRows(scratchPersisted, stagesAsc, scratchStageScores);
            }
        }

        return TournamentAdminPlayoffResultsDTO.builder()
                .tournamentAdminId(tournamentAdminId)
                .tipo(tipo)
                .stages(toStageColumns(stagesAsc))
                .rows(hcpRows)
                .scratchRows(scratchRows)
                .categoryLegend(categoryLegend)
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
                    .scratchRows(null)
                    .build();
        }

        // Recalcular todas las etapas antes del playoff
        for (TournamentAdminStage stage : stagesAsc) {
            stageService.calculateStageScores(tournamentAdminId, stage.getId());
        }

        ScoringConfigDTO config = scoringConfigService.getOrDefaultByTournamentAdminId(tournamentAdminId);
        int qualifiedPositions = config.getQualifiedPlayoffPositions();
        int qualifiedPositionsScratch = config.getQualifiedPlayoffPositionsScratch() != null ? config.getQualifiedPlayoffPositionsScratch() : 0;
        String hcpQualifiedMode = config.getHcpQualifiedMode() != null ? config.getHcpQualifiedMode() : "GLOBAL";

        List<Long> stageIdsDescForTieBreak = stagesAsc.stream()
                .map(TournamentAdminStage::getId)
                .collect(Collectors.toList());
        Collections.reverse(stageIdsDescForTieBreak);

        // ── HCP playoff ────────────────────────────────────────────────────────
        Map<Long, Map<Long, TournamentAdminStageScore>> hcpStageScores = loadStageScores(stagesAsc, "HCP");

        List<TournamentCategory> categories = Collections.emptyList();
        if ("CLASICO".equals(tipo) && "PER_CATEGORY".equals(hcpQualifiedMode)) {
            categories = loadCategoriesFromLastStage(stagesAsc);
        }

        List<TournamentAdminPlayoffResult> hcpResults = buildPlayoffResults(
                tournamentAdminId, stagesAsc, hcpStageScores, stageIdsDescForTieBreak,
                qualifiedPositions, "HCP", hcpQualifiedMode, categories);

        playoffResultRepository.deleteByTournamentAdminIdAndScoreType(tournamentAdminId, "HCP");
        playoffResultRepository.saveAll(hcpResults);

        // ── SCRATCH playoff (solo CLASICO y con clasificados configurados) ──────
        if ("CLASICO".equals(tipo)) {
            // Borrar resultados previos siempre (por si se redujo a 0)
            playoffResultRepository.deleteByTournamentAdminIdAndScoreType(tournamentAdminId, "SCRATCH");
            if (qualifiedPositionsScratch > 0) {
                Map<Long, Map<Long, TournamentAdminStageScore>> scratchStageScores = loadStageScores(stagesAsc, "SCRATCH");
                List<TournamentAdminPlayoffResult> scratchResults = buildPlayoffResults(
                        tournamentAdminId, stagesAsc, scratchStageScores, stageIdsDescForTieBreak,
                        qualifiedPositionsScratch, "SCRATCH", "GLOBAL", Collections.emptyList());
                playoffResultRepository.saveAll(scratchResults);
            }
        }

        return getResults(tournamentAdminId);
    }

    private List<TournamentAdminPlayoffResult> buildPlayoffResults(
            Long tournamentAdminId,
            List<TournamentAdminStage> stages,
            Map<Long, Map<Long, TournamentAdminStageScore>> stageScoresByStage,
            List<Long> stageIdsDescForTieBreak,
            int qualifiedPositions,
            String scoreType,
            String hcpQualifiedMode,
            List<TournamentCategory> categories) {

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

        if ("PER_CATEGORY".equals(hcpQualifiedMode) && !categories.isEmpty()) {
            return buildPerCategoryResults(tournamentAdminId, ordered, qualifiedPositions, categories);
        }

        // Modo GLOBAL (comportamiento original)
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

    /**
     * Modo PER_CATEGORY: clasifica los N primeros de cada categoría con HCP.
     * Mantiene ranking global (position), pero el flag qualified y categoryId se asignan
     * por categoría.
     */
    private List<TournamentAdminPlayoffResult> buildPerCategoryResults(
            Long tournamentAdminId,
            List<Candidate> ordered,
            int qualifiedPerCategory,
            List<TournamentCategory> categories) {

        // Determinar la categoría de cada jugador
        Map<Long, Long> playerCategoryId = new HashMap<>();
        for (Candidate candidate : ordered) {
            Player player = candidate.player;
            BigDecimal hcp = player.getHandicapIndex();
            String sexo = player.getSexo();
            if (hcp != null) {
                Long catId = resolveCategoryId(hcp, sexo, categories);
                if (catId != null) {
                    playerCategoryId.put(player.getId(), catId);
                }
            }
        }

        // Contar cuántos ya calificaron por cada categoría (respetando orden global)
        Map<Long, Integer> qualifiedCountByCategory = new HashMap<>();

        List<TournamentAdminPlayoffResult> results = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            Candidate candidate = ordered.get(i);
            int rankingPosition = i + 1;

            Long catId = playerCategoryId.get(candidate.player.getId());
            boolean qualified = false;
            if (catId != null) {
                int count = qualifiedCountByCategory.getOrDefault(catId, 0);
                if (count < qualifiedPerCategory) {
                    qualified = true;
                    qualifiedCountByCategory.put(catId, count + 1);
                }
            }

            results.add(TournamentAdminPlayoffResult.builder()
                    .tournamentAdmin(tournamentAdminRepository.getReferenceById(tournamentAdminId))
                    .player(candidate.player)
                    .scoreType("HCP")
                    .totalPoints(candidate.totalPoints)
                    .position(rankingPosition)
                    .qualified(qualified)
                    .categoryId(catId)
                    .build());
        }
        return results;
    }

    /** Resuelve la categoría de un jugador según handicap y sexo. */
    private Long resolveCategoryId(BigDecimal handicapIndex, String sexo, List<TournamentCategory> categories) {
        for (TournamentCategory cat : categories) {
            boolean sexoMatch = "X".equals(cat.getSexoCategoria())
                    || (sexo != null && sexo.equalsIgnoreCase(cat.getSexoCategoria()));
            boolean hcpMatch = handicapIndex.compareTo(cat.getHandicapMin()) >= 0
                    && handicapIndex.compareTo(cat.getHandicapMax()) <= 0;
            if (sexoMatch && hcpMatch) {
                return cat.getId();
            }
        }
        return null;
    }

    /**
     * Carga las categorías del torneo más reciente de la última etapa.
     * Es la misma referencia que usa getStageBoard().
     */
    private List<TournamentCategory> loadCategoriesFromLastStage(List<TournamentAdminStage> stagesAsc) {
        TournamentAdminStage lastStage = stagesAsc.get(stagesAsc.size() - 1);
        List<Tournament> stageTournaments = tournamentRepository.findByStageIdOrderByFechaInicioDesc(lastStage.getId());
        if (stageTournaments.isEmpty()) {
            return Collections.emptyList();
        }
        Tournament refTournament = stageTournaments.get(0);
        return categoryRepository.findByTournamentId(refTournament.getId());
    }

    /**
     * Construye la leyenda de categorías para el frontend (índice 0-based para colores).
     */
    private List<TournamentAdminPlayoffResultsDTO.CategoryLegendDTO> buildCategoryLegend(
            List<TournamentAdminStage> stagesAsc) {
        List<TournamentCategory> categories = loadCategoriesFromLastStage(stagesAsc);
        List<TournamentAdminPlayoffResultsDTO.CategoryLegendDTO> legend = new ArrayList<>();
        for (int i = 0; i < categories.size(); i++) {
            TournamentCategory cat = categories.get(i);
            legend.add(TournamentAdminPlayoffResultsDTO.CategoryLegendDTO.builder()
                    .categoryId(cat.getId())
                    .categoryName(cat.getNombre())
                    .categoryIndex(i)
                    .build());
        }
        return legend;
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
                            .categoryId(result.getCategoryId())
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
