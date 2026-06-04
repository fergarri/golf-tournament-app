package com.golf.tournament.service;

import com.golf.tournament.dto.leaderboard.TournamentScoreDTO;
import com.golf.tournament.dto.tournamentadmin.ScoringConfigDTO;
import com.golf.tournament.exception.ResourceNotFoundException;
import com.golf.tournament.model.*;
import com.golf.tournament.repository.HoleScoreRepository;
import com.golf.tournament.repository.ScorecardRepository;
import com.golf.tournament.repository.TournamentAdminRepository;
import com.golf.tournament.repository.TournamentRepository;
import com.golf.tournament.repository.TournamentScoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FrutalesScoreService {

    private final TournamentRepository tournamentRepository;
    private final TournamentAdminRepository tournamentAdminRepository;
    private final ScorecardRepository scorecardRepository;
    private final HoleScoreRepository holeScoreRepository;
    private final TournamentScoreRepository tournamentScoreRepository;
    private final TournamentAdminScoringConfigService scoringConfigService;

    @Transactional
    public List<TournamentScoreDTO> calculateScores(Long tournamentId) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", "id", tournamentId));

        int multiplier = Boolean.TRUE.equals(tournament.getDoublePoints()) ? 2 : 1;

        ScoringConfigDTO config = loadScoringConfig(tournamentId);
        Map<Integer, Integer> positionPointsMap = buildPositionPointsMap(config);

        tournamentScoreRepository.deleteAllByTournamentIdAndScoreType(tournamentId, TournamentScore.SCORE_TYPE_GLOBAL);
        tournamentScoreRepository.flush();

        List<Scorecard> scorecards = scorecardRepository.findByTournamentIdAndStatusIn(
                tournamentId, List.of(ScorecardStatus.DELIVERED, ScorecardStatus.CANCELLED));

        List<PlayerScoreData> deliveredData = scorecards.stream()
                .filter(s -> s.getStatus() == ScorecardStatus.DELIVERED)
                .map(this::buildPlayerScoreData)
                .collect(Collectors.toList());

        List<PlayerScoreData> cancelledData = scorecards.stream()
                .filter(s -> s.getStatus() == ScorecardStatus.CANCELLED)
                .map(this::buildPlayerScoreData)
                .collect(Collectors.toList());

        deliveredData.sort(buildComparator(config.getTieBreakMode()));

        List<TournamentScore> persistedScores = new ArrayList<>();

        for (int i = 0; i < deliveredData.size(); i++) {
            PlayerScoreData data = deliveredData.get(i);
            int deliveredRank = i + 1;
            int posPoints = positionPointsMap.getOrDefault(deliveredRank, config.getRemainingPositionsPoints()) * multiplier;
            int birdiePoints = data.birdieCount * config.getBirdiePoints() * multiplier;
            int eaglePoints = data.eagleCount * config.getEaglePoints() * multiplier;
            int acePoints = data.aceCount * config.getAcePoints() * multiplier;
            int participationPoints = config.getParticipationPoints() * multiplier;
            int total = posPoints + birdiePoints + eaglePoints + acePoints + participationPoints;

            TournamentScore score = TournamentScore.builder()
                    .tournament(tournament)
                    .scorecard(data.scorecard)
                    .player(data.scorecard.getPlayer())
                    .scoreType(TournamentScore.SCORE_TYPE_GLOBAL)
                    .position(deliveredRank)
                    .positionPoints(posPoints)
                    .birdieCount(data.birdieCount)
                    .birdiePoints(birdiePoints)
                    .eagleCount(data.eagleCount)
                    .eaglePoints(eaglePoints)
                    .aceCount(data.aceCount)
                    .acePoints(acePoints)
                    .participationPoints(participationPoints)
                    .totalPoints(total)
                    .build();

            persistedScores.add(score);
        }

        List<CalculatedScoreData> cancelledCalculated = new ArrayList<>();
        for (PlayerScoreData data : cancelledData) {
            int participationPoints = data.scoresByHole.isEmpty() ? 0 : config.getParticipationPoints() * multiplier;

            TournamentScore score = TournamentScore.builder()
                    .tournament(tournament)
                    .scorecard(data.scorecard)
                    .player(data.scorecard.getPlayer())
                    .scoreType(TournamentScore.SCORE_TYPE_GLOBAL)
                    .position(null)
                    .positionPoints(0)
                    .birdieCount(data.birdieCount)
                    .birdiePoints(0)
                    .eagleCount(data.eagleCount)
                    .eaglePoints(0)
                    .aceCount(data.aceCount)
                    .acePoints(0)
                    .participationPoints(participationPoints)
                    .totalPoints(participationPoints)
                    .build();

            cancelledCalculated.add(new CalculatedScoreData(score, data));
        }

        cancelledCalculated.sort(buildCancelledRankingComparator());
        for (int i = 0; i < cancelledCalculated.size(); i++) {
            cancelledCalculated.get(i).score.setPosition(deliveredData.size() + i + 1);
        }

        persistedScores.addAll(cancelledCalculated.stream()
                .map(cs -> cs.score)
                .collect(Collectors.toList()));

        List<Scorecard> disqualifiedScorecards = scorecardRepository
                .findByTournamentIdAndStatus(tournamentId, ScorecardStatus.DISQUALIFIED);

        for (Scorecard sc : disqualifiedScorecards) {
            TournamentScore score = TournamentScore.builder()
                    .tournament(tournament)
                    .scorecard(sc)
                    .player(sc.getPlayer())
                    .scoreType(TournamentScore.SCORE_TYPE_GLOBAL)
                    .position(null)
                    .positionPoints(0)
                    .birdieCount(0)
                    .birdiePoints(0)
                    .eagleCount(0)
                    .eaglePoints(0)
                    .aceCount(0)
                    .acePoints(0)
                    .participationPoints(0)
                    .totalPoints(0)
                    .build();
            persistedScores.add(score);
        }

        tournamentScoreRepository.saveAll(persistedScores);

        log.info("Frutales scores calculados para torneo {}: {} delivered, {} cancelled, multiplier={}, tieBreakMode={}",
                tournamentId, deliveredData.size(), cancelledData.size(), multiplier, config.getTieBreakMode());

        return getScores(tournamentId);
    }

    @Transactional(readOnly = true)
    public List<TournamentScoreDTO> getScores(Long tournamentId) {
        tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", "id", tournamentId));

        List<TournamentScore> scores = tournamentScoreRepository
                .findByTournamentIdAndScoreTypeOrderByTotalPointsDesc(tournamentId, TournamentScore.SCORE_TYPE_GLOBAL);

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
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Carga la configuración de puntuación para el torneo.
     * Busca el TournamentAdmin que contiene este torneo en alguna de sus etapas y obtiene su config.
     * Si no existe TournamentAdmin o no tiene config, retorna los valores por defecto.
     */
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

    private Comparator<PlayerScoreData> buildComparator(String tieBreakMode) {
        if ("GROSS_BACK9".equals(tieBreakMode)) {
            return buildGrossBack9Comparator();
        }
        return buildNetoHcpHoleComparator();
    }

    /**
     * Desempate Frutales: neto ascendente → handicap índice ascendente → hoyo por hoyo desde el último
     */
    private Comparator<PlayerScoreData> buildNetoHcpHoleComparator() {
        return (a, b) -> {
            BigDecimal netoA = a.neto != null ? a.neto : BigDecimal.valueOf(9999);
            BigDecimal netoB = b.neto != null ? b.neto : BigDecimal.valueOf(9999);
            int netoCompare = netoA.compareTo(netoB);
            if (netoCompare != 0) return netoCompare;

            BigDecimal hcpA = a.handicapIndex != null ? a.handicapIndex : BigDecimal.valueOf(999);
            BigDecimal hcpB = b.handicapIndex != null ? b.handicapIndex : BigDecimal.valueOf(999);
            int hcpCompare = hcpA.compareTo(hcpB);
            if (hcpCompare != 0) return hcpCompare;

            return compareHoleByHoleFromLast(a, b);
        };
    }

    /**
     * Desempate vuelta gross: gross acumulado hoyos 10-18 ascendente → hoyo 18 → 17 → ... → 10
     */
    private Comparator<PlayerScoreData> buildGrossBack9Comparator() {
        return (a, b) -> {
            int backNineA = sumHoles(a.scoresByHole, 10, 18);
            int backNineB = sumHoles(b.scoresByHole, 10, 18);
            int backNineCompare = Integer.compare(backNineA, backNineB);
            if (backNineCompare != 0) return backNineCompare;

            for (int hole = 18; hole >= 10; hole--) {
                int scoreA = a.scoresByHole.getOrDefault(hole, 99);
                int scoreB = b.scoresByHole.getOrDefault(hole, 99);
                if (scoreA != scoreB) return Integer.compare(scoreA, scoreB);
            }
            for (int hole = 9; hole >= 1; hole--) {
                int scoreA = a.scoresByHole.getOrDefault(hole, 99);
                int scoreB = b.scoresByHole.getOrDefault(hole, 99);
                if (scoreA != scoreB) return Integer.compare(scoreA, scoreB);
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

    private Comparator<CalculatedScoreData> buildCancelledRankingComparator() {
        return (a, b) -> {
            int totalCompare = Integer.compare(b.score.getTotalPoints(), a.score.getTotalPoints());
            if (totalCompare != 0) return totalCompare;

            BigDecimal hcpA = a.playerData.handicapIndex != null ? a.playerData.handicapIndex : BigDecimal.valueOf(999);
            BigDecimal hcpB = b.playerData.handicapIndex != null ? b.playerData.handicapIndex : BigDecimal.valueOf(999);
            int hcpCompare = hcpA.compareTo(hcpB);
            if (hcpCompare != 0) return hcpCompare;

            return compareHoleByHoleFromLast(a.playerData, b.playerData);
        };
    }

    private int compareHoleByHoleFromLast(PlayerScoreData a, PlayerScoreData b) {
        int startHole = Math.max(a.maxHole, b.maxHole);
        for (int hole = startHole; hole >= 1; hole--) {
            int scoreA = a.scoresByHole.getOrDefault(hole, 99);
            int scoreB = b.scoresByHole.getOrDefault(hole, 99);
            if (scoreA != scoreB) return Integer.compare(scoreA, scoreB);
        }
        return 0;
    }

    TournamentScoreDTO convertToDTO(TournamentScore score) {
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

    PlayerScoreData buildPlayerScoreData(Scorecard scorecard) {
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

        int birdieCount = 0;
        int eagleCount = 0;
        int aceCount = 0;

        for (HoleScore hs : holeScores) {
            if (hs.getGolpesPropio() == null) continue;
            int golpes = hs.getGolpesPropio();
            int par = hs.getHole().getPar();

            if (golpes == 1) {
                aceCount++;
            } else if (golpes == par - 2) {
                eagleCount++;
            } else if (golpes == par - 1) {
                birdieCount++;
            }
        }

        Map<Integer, Integer> scoresByHole = new HashMap<>();
        for (HoleScore hs : holeScores) {
            if (hs.getGolpesPropio() != null) {
                scoresByHole.put(hs.getHole().getNumeroHoyo(), hs.getGolpesPropio());
            }
        }

        int maxHole = holeScores.stream()
                .mapToInt(hs -> hs.getHole().getNumeroHoyo())
                .max()
                .orElse(9);

        return new PlayerScoreData(
                scorecard, neto,
                scorecard.getPlayer().getHandicapIndex(),
                birdieCount, eagleCount, aceCount,
                scoresByHole, maxHole
        );
    }

    static class PlayerScoreData {
        final Scorecard scorecard;
        final BigDecimal neto;
        final BigDecimal handicapIndex;
        final int birdieCount;
        final int eagleCount;
        final int aceCount;
        final Map<Integer, Integer> scoresByHole;
        final int maxHole;

        PlayerScoreData(Scorecard scorecard, BigDecimal neto, BigDecimal handicapIndex,
                        int birdieCount, int eagleCount, int aceCount,
                        Map<Integer, Integer> scoresByHole, int maxHole) {
            this.scorecard = scorecard;
            this.neto = neto;
            this.handicapIndex = handicapIndex;
            this.birdieCount = birdieCount;
            this.eagleCount = eagleCount;
            this.aceCount = aceCount;
            this.scoresByHole = scoresByHole;
            this.maxHole = maxHole;
        }
    }

    static class CalculatedScoreData {
        final TournamentScore score;
        final PlayerScoreData playerData;

        CalculatedScoreData(TournamentScore score, PlayerScoreData playerData) {
            this.score = score;
            this.playerData = playerData;
        }
    }
}
