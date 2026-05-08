package com.golf.tournament.service;

import com.golf.tournament.dto.leaderboard.TournamentScoreDTO;
import com.golf.tournament.dto.tournamentadmin.ScoringConfigDTO;
import com.golf.tournament.exception.ResourceNotFoundException;
import com.golf.tournament.model.*;
import com.golf.tournament.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio de cálculo de puntos para torneos de tipo CLÁSICO (bajo un Torneo Administrativo).
 *
 * Genera dos tipos de registros por torneo:
 *  - CATEGORY: puntaje por categoría creada en el torneo, ordenado por score neto ascendente.
 *  - SCRATCH:  puntaje scratch global, ordenado por score gross ascendente.
 *
 * Cada tipo de puntuación es independiente: un jugador puede acumular puntos en su
 * categoría (CATEGORY) y puntos scratch (SCRATCH) por separado.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClasicScoreService {

    private final TournamentRepository tournamentRepository;
    private final TournamentAdminRepository tournamentAdminRepository;
    private final ScorecardRepository scorecardRepository;
    private final HoleScoreRepository holeScoreRepository;
    private final TournamentScoreRepository tournamentScoreRepository;
    private final TournamentCategoryRepository categoryRepository;
    private final TournamentInscriptionRepository inscriptionRepository;
    private final TournamentAdminScoringConfigService scoringConfigService;

    // ── Cálculo ────────────────────────────────────────────────────────────────

    @Transactional
    public List<TournamentScoreDTO> calculateScores(Long tournamentId) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", "id", tournamentId));

        int multiplier = Boolean.TRUE.equals(tournament.getDoublePoints()) ? 2 : 1;
        ScoringConfigDTO config = loadScoringConfig(tournamentId);
        Map<Integer, Integer> positionPointsMap = buildPositionPointsMap(config);

        // Limpiar registros CATEGORY y SCRATCH anteriores para este torneo
        tournamentScoreRepository.deleteAllByTournamentIdAndScoreType(tournamentId, TournamentScore.SCORE_TYPE_CATEGORY);
        tournamentScoreRepository.deleteAllByTournamentIdAndScoreType(tournamentId, TournamentScore.SCORE_TYPE_SCRATCH);
        tournamentScoreRepository.flush();

        List<TournamentScore> allScores = new ArrayList<>();

        // ── Puntajes por CATEGORÍA ─────────────────────────────────────────────
        List<TournamentCategory> categories = categoryRepository.findByTournamentId(tournamentId);
        for (TournamentCategory category : categories) {
            List<TournamentInscription> inscriptions = inscriptionRepository
                    .findByTournamentIdAndCategoryId(tournamentId, category.getId());
            List<Long> playerIds = inscriptions.stream()
                    .map(i -> i.getPlayer().getId())
                    .collect(Collectors.toList());

            List<Scorecard> delivered = scorecardRepository.findByTournamentIdAndStatusIn(
                    tournamentId, List.of(ScorecardStatus.DELIVERED, ScorecardStatus.CANCELLED))
                    .stream()
                    .filter(sc -> playerIds.contains(sc.getPlayer().getId()))
                    .collect(Collectors.toList());

            List<FrutalesScoreService.PlayerScoreData> deliveredData = delivered.stream()
                    .filter(s -> s.getStatus() == ScorecardStatus.DELIVERED)
                    .map(this::buildPlayerScoreData)
                    .collect(Collectors.toList());

            List<FrutalesScoreService.PlayerScoreData> cancelledData = delivered.stream()
                    .filter(s -> s.getStatus() == ScorecardStatus.CANCELLED)
                    .map(this::buildPlayerScoreData)
                    .collect(Collectors.toList());

            // Ordenar por neto ascendente → HCP → hoyo por hoyo
            deliveredData.sort(buildNetoComparator());

            for (int i = 0; i < deliveredData.size(); i++) {
                FrutalesScoreService.PlayerScoreData data = deliveredData.get(i);
                int rank = i + 1;
                int posPoints = positionPointsMap.getOrDefault(rank, config.getRemainingPositionsPoints()) * multiplier;
                int birdiePoints = data.birdieCount * config.getBirdiePoints() * multiplier;
                int eaglePoints = data.eagleCount * config.getEaglePoints() * multiplier;
                int acePoints = data.aceCount * config.getAcePoints() * multiplier;
                int participationPoints = config.getParticipationPoints() * multiplier;
                int total = posPoints + birdiePoints + eaglePoints + acePoints + participationPoints;

                allScores.add(TournamentScore.builder()
                        .tournament(tournament)
                        .scorecard(data.scorecard)
                        .player(data.scorecard.getPlayer())
                        .scoreType(TournamentScore.SCORE_TYPE_CATEGORY)
                        .categoryId(category.getId())
                        .position(rank)
                        .positionPoints(posPoints)
                        .birdieCount(data.birdieCount)
                        .birdiePoints(birdiePoints)
                        .eagleCount(data.eagleCount)
                        .eaglePoints(eaglePoints)
                        .aceCount(data.aceCount)
                        .acePoints(acePoints)
                        .participationPoints(participationPoints)
                        .totalPoints(total)
                        .build());
            }

            // Cancelados de esta categoría: solo participación
            for (FrutalesScoreService.PlayerScoreData data : cancelledData) {
                int participationPoints = config.getParticipationPoints() * multiplier;
                allScores.add(TournamentScore.builder()
                        .tournament(tournament)
                        .scorecard(data.scorecard)
                        .player(data.scorecard.getPlayer())
                        .scoreType(TournamentScore.SCORE_TYPE_CATEGORY)
                        .categoryId(category.getId())
                        .position(null)
                        .positionPoints(0)
                        .birdieCount(0).birdiePoints(0)
                        .eagleCount(0).eaglePoints(0)
                        .aceCount(0).acePoints(0)
                        .participationPoints(participationPoints)
                        .totalPoints(participationPoints)
                        .build());
            }
        }

        // ── Puntajes SCRATCH ──────────────────────────────────────────────────
        List<Scorecard> allDelivered = scorecardRepository.findByTournamentIdAndStatusIn(
                tournamentId, List.of(ScorecardStatus.DELIVERED, ScorecardStatus.CANCELLED));

        List<FrutalesScoreService.PlayerScoreData> scratchDelivered = allDelivered.stream()
                .filter(s -> s.getStatus() == ScorecardStatus.DELIVERED)
                .map(this::buildPlayerScoreData)
                .collect(Collectors.toList());

        List<FrutalesScoreService.PlayerScoreData> scratchCancelled = allDelivered.stream()
                .filter(s -> s.getStatus() == ScorecardStatus.CANCELLED)
                .map(this::buildPlayerScoreData)
                .collect(Collectors.toList());

        // Ordenar por gross ascendente → hoyo 18→1
        scratchDelivered.sort(buildGrossComparator());

        for (int i = 0; i < scratchDelivered.size(); i++) {
            FrutalesScoreService.PlayerScoreData data = scratchDelivered.get(i);
            int rank = i + 1;
            int posPoints = positionPointsMap.getOrDefault(rank, config.getRemainingPositionsPoints()) * multiplier;
            int birdiePoints = data.birdieCount * config.getBirdiePoints() * multiplier;
            int eaglePoints = data.eagleCount * config.getEaglePoints() * multiplier;
            int acePoints = data.aceCount * config.getAcePoints() * multiplier;
            int participationPoints = config.getParticipationPoints() * multiplier;
            int total = posPoints + birdiePoints + eaglePoints + acePoints + participationPoints;

            allScores.add(TournamentScore.builder()
                    .tournament(tournament)
                    .scorecard(data.scorecard)
                    .player(data.scorecard.getPlayer())
                    .scoreType(TournamentScore.SCORE_TYPE_SCRATCH)
                    .categoryId(null)
                    .position(rank)
                    .positionPoints(posPoints)
                    .birdieCount(data.birdieCount)
                    .birdiePoints(birdiePoints)
                    .eagleCount(data.eagleCount)
                    .eaglePoints(eaglePoints)
                    .aceCount(data.aceCount)
                    .acePoints(acePoints)
                    .participationPoints(participationPoints)
                    .totalPoints(total)
                    .build());
        }

        for (FrutalesScoreService.PlayerScoreData data : scratchCancelled) {
            int participationPoints = config.getParticipationPoints() * multiplier;
            allScores.add(TournamentScore.builder()
                    .tournament(tournament)
                    .scorecard(data.scorecard)
                    .player(data.scorecard.getPlayer())
                    .scoreType(TournamentScore.SCORE_TYPE_SCRATCH)
                    .categoryId(null)
                    .position(null)
                    .positionPoints(0)
                    .birdieCount(0).birdiePoints(0)
                    .eagleCount(0).eaglePoints(0)
                    .aceCount(0).acePoints(0)
                    .participationPoints(participationPoints)
                    .totalPoints(participationPoints)
                    .build());
        }

        // DS global: sin puntos
        List<Scorecard> dsCards = scorecardRepository.findByTournamentIdAndStatus(tournamentId, ScorecardStatus.DISQUALIFIED);
        for (Scorecard sc : dsCards) {
            // Agregar un registro SCRATCH con 0 puntos (para que aparezca en el tab scratch)
            allScores.add(TournamentScore.builder()
                    .tournament(tournament)
                    .scorecard(sc)
                    .player(sc.getPlayer())
                    .scoreType(TournamentScore.SCORE_TYPE_SCRATCH)
                    .categoryId(null)
                    .position(null).positionPoints(0)
                    .birdieCount(0).birdiePoints(0)
                    .eagleCount(0).eaglePoints(0)
                    .aceCount(0).acePoints(0)
                    .participationPoints(0).totalPoints(0)
                    .build());
        }

        tournamentScoreRepository.saveAll(allScores);

        log.info("Clásic scores calculados para torneo {}: {} categorías, {} scratch delivered",
                tournamentId, categories.size(), scratchDelivered.size());

        return getScores(tournamentId);
    }

    // ── Consulta ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TournamentScoreDTO> getScores(Long tournamentId) {
        tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", "id", tournamentId));

        // Cargar nombres de categorías
        Map<Long, String> categoryNames = categoryRepository.findByTournamentId(tournamentId)
                .stream().collect(Collectors.toMap(TournamentCategory::getId, TournamentCategory::getNombre));

        List<TournamentScore> categoryScores = tournamentScoreRepository
                .findByTournamentIdAndScoreTypeOrderByTotalPointsDesc(tournamentId, TournamentScore.SCORE_TYPE_CATEGORY);

        List<TournamentScore> scratchScores = tournamentScoreRepository
                .findByTournamentIdAndScoreTypeOrderByTotalPointsDesc(tournamentId, TournamentScore.SCORE_TYPE_SCRATCH);

        List<TournamentScoreDTO> result = new ArrayList<>();

        // Categorías: ordered by position dentro de cada categoría
        result.addAll(buildOrderedScoreDTOs(categoryScores, categoryNames));
        // Scratch
        result.addAll(buildOrderedScoreDTOs(scratchScores, categoryNames));

        return result;
    }

    private List<TournamentScoreDTO> buildOrderedScoreDTOs(List<TournamentScore> scores, Map<Long, String> categoryNames) {
        List<TournamentScore> positioned = scores.stream()
                .filter(s -> s.getPosition() != null)
                .sorted(Comparator.comparingInt(TournamentScore::getPosition))
                .collect(Collectors.toList());

        List<TournamentScore> nmScores = scores.stream()
                .filter(s -> s.getPosition() == null && s.getScorecard().getStatus() != ScorecardStatus.DISQUALIFIED)
                .sorted(Comparator.comparingInt(TournamentScore::getTotalPoints).reversed())
                .collect(Collectors.toList());

        List<TournamentScore> dsScores = scores.stream()
                .filter(s -> s.getScorecard().getStatus() == ScorecardStatus.DISQUALIFIED)
                .collect(Collectors.toList());

        List<TournamentScore> ordered = new ArrayList<>();
        ordered.addAll(positioned);
        ordered.addAll(nmScores);
        ordered.addAll(dsScores);

        return ordered.stream()
                .map(s -> {
                    TournamentScoreDTO dto = convertToDTO(s);
                    if (s.getCategoryId() != null) {
                        dto.setCategoryName(categoryNames.get(s.getCategoryId()));
                    }
                    return dto;
                })
                .collect(Collectors.toList());
    }

    // ── Comparadores ────────────────────────────────────────────────────────────

    /** Neto ascendente → HCP índice ascendente → hoyo por hoyo desde el último */
    private Comparator<FrutalesScoreService.PlayerScoreData> buildNetoComparator() {
        return (a, b) -> {
            BigDecimal netoA = a.neto != null ? a.neto : BigDecimal.valueOf(9999);
            BigDecimal netoB = b.neto != null ? b.neto : BigDecimal.valueOf(9999);
            int cmp = netoA.compareTo(netoB);
            if (cmp != 0) return cmp;

            BigDecimal hcpA = a.handicapIndex != null ? a.handicapIndex : BigDecimal.valueOf(999);
            BigDecimal hcpB = b.handicapIndex != null ? b.handicapIndex : BigDecimal.valueOf(999);
            cmp = hcpA.compareTo(hcpB);
            if (cmp != 0) return cmp;

            return compareHoleByHoleFromLast(a, b);
        };
    }

    /** Gross ascendente → suma vuelta gross (hoyos 10-maxHole) ascendente → hoyo por hoyo desde el último */
    private Comparator<FrutalesScoreService.PlayerScoreData> buildGrossComparator() {
        return (a, b) -> {
            int grossA = sumAll(a.scoresByHole);
            int grossB = sumAll(b.scoresByHole);
            int cmp = Integer.compare(grossA, grossB);
            if (cmp != 0) return cmp;

            // Tiebreaker 1: suma vuelta gross (hoyos 10 al último hoyo)
            int endHole = Math.max(a.maxHole, b.maxHole);
            if (endHole > 9) {
                int backNineA = sumHoles(a.scoresByHole, 10, endHole);
                int backNineB = sumHoles(b.scoresByHole, 10, endHole);
                cmp = Integer.compare(backNineA, backNineB);
                if (cmp != 0) return cmp;
            }

            // Tiebreaker 2: hoyo por hoyo desde el último
            for (int hole = endHole; hole >= 1; hole--) {
                int sa = a.scoresByHole.getOrDefault(hole, 99);
                int sb = b.scoresByHole.getOrDefault(hole, 99);
                if (sa != sb) return Integer.compare(sa, sb);
            }
            return 0;
        };
    }

    private int sumHoles(Map<Integer, Integer> scoresByHole, int from, int to) {
        int sum = 0;
        for (int h = from; h <= to; h++) {
            sum += scoresByHole.getOrDefault(h, 99);
        }
        return sum;
    }

    private int sumAll(Map<Integer, Integer> scoresByHole) {
        return scoresByHole.values().stream().mapToInt(Integer::intValue).sum();
    }

    private int compareHoleByHoleFromLast(FrutalesScoreService.PlayerScoreData a, FrutalesScoreService.PlayerScoreData b) {
        int startHole = Math.max(a.maxHole, b.maxHole);
        for (int hole = startHole; hole >= 1; hole--) {
            int sa = a.scoresByHole.getOrDefault(hole, 99);
            int sb = b.scoresByHole.getOrDefault(hole, 99);
            if (sa != sb) return Integer.compare(sa, sb);
        }
        return 0;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private ScoringConfigDTO loadScoringConfig(Long tournamentId) {
        return tournamentAdminRepository.findByTournamentInAnyStage(tournamentId)
                .map(admin -> scoringConfigService.getOrDefaultByTournamentAdminId(admin.getId()))
                .orElseGet(() -> scoringConfigService.getOrDefaultByTournamentAdminId(-1L));
    }

    private Map<Integer, Integer> buildPositionPointsMap(ScoringConfigDTO config) {
        Map<Integer, Integer> map = new HashMap<>();
        if (config.getPositionPoints() != null) {
            for (ScoringConfigDTO.PositionPointsDTO pp : config.getPositionPoints()) {
                map.put(pp.getPosition(), pp.getPoints());
            }
        }
        return map;
    }

    private FrutalesScoreService.PlayerScoreData buildPlayerScoreData(Scorecard scorecard) {
        List<HoleScore> holeScores = holeScoreRepository.findByScorecardId(scorecard.getId());

        Integer gross = null;
        if (ScorecardStatus.DELIVERED.equals(scorecard.getStatus())) {
            gross = holeScores.stream()
                    .filter(hs -> hs.getGolpesPropio() != null)
                    .mapToInt(HoleScore::getGolpesPropio)
                    .sum();
        }

        BigDecimal hcp = scorecard.getHandicapCourse() != null ? scorecard.getHandicapCourse() : BigDecimal.ZERO;
        BigDecimal neto = gross != null ? BigDecimal.valueOf(gross).subtract(hcp) : null;

        int birdieCount = 0, eagleCount = 0, aceCount = 0;
        for (HoleScore hs : holeScores) {
            if (hs.getGolpesPropio() == null) continue;
            int golpes = hs.getGolpesPropio();
            int par = hs.getHole().getPar();
            if (golpes == 1) aceCount++;
            else if (golpes == par - 2) eagleCount++;
            else if (golpes == par - 1) birdieCount++;
        }

        Map<Integer, Integer> scoresByHole = new HashMap<>();
        for (HoleScore hs : holeScores) {
            if (hs.getGolpesPropio() != null) {
                scoresByHole.put(hs.getHole().getNumeroHoyo(), hs.getGolpesPropio());
            }
        }

        int maxHole = holeScores.stream().mapToInt(hs -> hs.getHole().getNumeroHoyo()).max().orElse(9);

        return new FrutalesScoreService.PlayerScoreData(
                scorecard, neto, scorecard.getPlayer().getHandicapIndex(),
                birdieCount, eagleCount, aceCount, scoresByHole, maxHole);
    }

    private TournamentScoreDTO convertToDTO(TournamentScore score) {
        Scorecard sc = score.getScorecard();
        Player player = score.getPlayer();

        Integer scoreGross = null;
        BigDecimal scoreNeto = null;

        if (sc.getStatus() == ScorecardStatus.DELIVERED) {
            List<HoleScore> holeScores = holeScoreRepository.findByScorecardId(sc.getId());
            scoreGross = holeScores.stream()
                    .filter(hs -> hs.getGolpesPropio() != null)
                    .mapToInt(HoleScore::getGolpesPropio)
                    .sum();
            BigDecimal hcp = sc.getHandicapCourse() != null ? sc.getHandicapCourse() : BigDecimal.ZERO;
            scoreNeto = BigDecimal.valueOf(scoreGross).subtract(hcp);
        }

        return TournamentScoreDTO.builder()
                .scorecardId(sc.getId())
                .playerId(player.getId())
                .playerName(player.getApellido() + " " + player.getNombre())
                .matricula(player.getMatricula())
                .position(score.getPosition())
                .handicapIndex(player.getHandicapIndex())
                .handicapCourse(sc.getHandicapCourse())
                .scoreGross(scoreGross)
                .scoreNeto(scoreNeto)
                .status(sc.getStatus().name())
                .birdieCount(score.getBirdieCount())
                .eagleCount(score.getEagleCount())
                .aceCount(score.getAceCount())
                .positionPoints(score.getPositionPoints())
                .birdiePoints(score.getBirdiePoints())
                .eaglePoints(score.getEaglePoints())
                .acePoints(score.getAcePoints())
                .participationPoints(score.getParticipationPoints())
                .totalPoints(score.getTotalPoints())
                .scoreType(score.getScoreType())
                .categoryId(score.getCategoryId())
                .build();
    }
}
