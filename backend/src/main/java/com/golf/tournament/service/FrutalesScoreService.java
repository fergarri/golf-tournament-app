package com.golf.tournament.service;

import com.golf.tournament.dto.leaderboard.FrutalesScoreDTO;
import com.golf.tournament.exception.BadRequestException;
import com.golf.tournament.exception.ResourceNotFoundException;
import com.golf.tournament.model.*;
import com.golf.tournament.repository.FrutalesScoreRepository;
import com.golf.tournament.repository.HoleScoreRepository;
import com.golf.tournament.repository.ScorecardRepository;
import com.golf.tournament.repository.TournamentRepository;
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
    private final ScorecardRepository scorecardRepository;
    private final HoleScoreRepository holeScoreRepository;
    private final FrutalesScoreRepository frutalesScoreRepository;

    private static final Map<Integer, Integer> POSITION_POINTS = Map.of(
            1, 12,
            2, 10,
            3, 8,
            4, 6,
            5, 4,
            6, 2
    );

    @Transactional
    public List<FrutalesScoreDTO> calculateScores(Long tournamentId) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", "id", tournamentId));

        if (!"FRUTALES".equals(tournament.getTipo())) {
            throw new BadRequestException("El cálculo de puntos Frutales solo aplica a torneos de tipo FRUTALES");
        }

        int multiplier = Boolean.TRUE.equals(tournament.getDoublePoints()) ? 2 : 1;

        frutalesScoreRepository.deleteAllByTournamentId(tournamentId);
        frutalesScoreRepository.flush();

        // DELIVERED + CANCELLED are scored.
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

        // 1) DELIVERED: rank by net + tie-breakers to assign position points.
        deliveredData.sort(buildFrutalesComparator());

        List<CalculatedScoreData> calculatedScores = new ArrayList<>();

        for (int i = 0; i < deliveredData.size(); i++) {
            PlayerScoreData data = deliveredData.get(i);
            int deliveredRank = i + 1;
            int posPoints = POSITION_POINTS.getOrDefault(deliveredRank, 1) * multiplier;
            int birdiePoints = data.birdieCount * multiplier;
            int eaglePoints = data.eagleCount * 5 * multiplier;
            int acePoints = data.aceCount * 10 * multiplier;
            int participationPoints = 1 * multiplier;
            int total = posPoints + birdiePoints + eaglePoints + acePoints + participationPoints;

            FrutalesScore score = FrutalesScore.builder()
                    .tournament(tournament)
                    .scorecard(data.scorecard)
                    .player(data.scorecard.getPlayer())
                    .position(null)
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

            calculatedScores.add(new CalculatedScoreData(score, data));
        }

        // 2) CANCELLED: only participation + birdie/eagle/ace.
        for (PlayerScoreData data : cancelledData) {
            int birdiePoints = data.birdieCount * multiplier;
            int eaglePoints = data.eagleCount * 5 * multiplier;
            int acePoints = data.aceCount * 10 * multiplier;
            int participationPoints = 1 * multiplier;
            int total = birdiePoints + eaglePoints + acePoints + participationPoints;

            FrutalesScore score = FrutalesScore.builder()
                    .tournament(tournament)
                    .scorecard(data.scorecard)
                    .player(data.scorecard.getPlayer())
                    .position(null)
                    .positionPoints(0)
                    .birdieCount(data.birdieCount)
                    .birdiePoints(birdiePoints)
                    .eagleCount(data.eagleCount)
                    .eaglePoints(eaglePoints)
                    .aceCount(data.aceCount)
                    .acePoints(acePoints)
                    .participationPoints(participationPoints)
                    .totalPoints(total)
                    .build();

            calculatedScores.add(new CalculatedScoreData(score, data));
        }

        // 3) Final positions by total_points across DELIVERED + CANCELLED.
        // Tie on total_points: DELIVERED wins over CANCELLED.
        calculatedScores.sort(buildFinalRankingComparator());
        for (int i = 0; i < calculatedScores.size(); i++) {
            calculatedScores.get(i).score.setPosition(i + 1);
        }

        List<FrutalesScore> persistedScores = calculatedScores.stream()
                .map(cs -> cs.score)
                .collect(Collectors.toList());

        // DS always included with 0 points.
        List<Scorecard> disqualifiedScorecards = scorecardRepository
                .findByTournamentIdAndStatus(tournamentId, ScorecardStatus.DISQUALIFIED);

        for (Scorecard sc : disqualifiedScorecards) {
            FrutalesScore score = FrutalesScore.builder()
                    .tournament(tournament)
                    .scorecard(sc)
                    .player(sc.getPlayer())
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

        frutalesScoreRepository.saveAll(persistedScores);

        log.info("Frutales scores calculated for tournament {}: {} delivered, {} cancelled, multiplier={}",
                tournamentId, deliveredData.size(), cancelledData.size(), multiplier);

        return getScores(tournamentId);
    }

    @Transactional(readOnly = true)
    public List<FrutalesScoreDTO> getScores(Long tournamentId) {
        tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", "id", tournamentId));

        List<FrutalesScore> scores = frutalesScoreRepository.findByTournamentIdOrderByTotalPointsDesc(tournamentId);

        List<FrutalesScore> positioned = scores.stream()
                .filter(s -> s.getPosition() != null)
                .sorted(Comparator.comparingInt(FrutalesScore::getPosition))
                .collect(Collectors.toList());

        List<FrutalesScore> nmScores = scores.stream()
                .filter(s -> s.getPosition() == null && s.getScorecard().getStatus() != ScorecardStatus.DISQUALIFIED)
                .sorted(Comparator.comparingInt(FrutalesScore::getTotalPoints).reversed())
                .collect(Collectors.toList());

        List<FrutalesScore> dsScores = scores.stream()
                .filter(s -> s.getScorecard().getStatus() == ScorecardStatus.DISQUALIFIED)
                .collect(Collectors.toList());

        List<FrutalesScore> ordered = new ArrayList<>();
        ordered.addAll(positioned);
        ordered.addAll(nmScores);
        ordered.addAll(dsScores);

        return ordered.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    private FrutalesScoreDTO convertToDTO(FrutalesScore score) {
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

        return FrutalesScoreDTO.builder()
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
                .build();
    }

    private PlayerScoreData buildPlayerScoreData(Scorecard scorecard) {
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

    private Comparator<PlayerScoreData> buildFrutalesComparator() {
        return (a, b) -> {
            BigDecimal netoA = a.neto != null ? a.neto : BigDecimal.valueOf(9999);
            BigDecimal netoB = b.neto != null ? b.neto : BigDecimal.valueOf(9999);
            int netoCompare = netoA.compareTo(netoB);
            if (netoCompare != 0) return netoCompare;

            BigDecimal hcpA = a.handicapIndex != null ? a.handicapIndex : BigDecimal.valueOf(999);
            BigDecimal hcpB = b.handicapIndex != null ? b.handicapIndex : BigDecimal.valueOf(999);
            int hcpCompare = hcpA.compareTo(hcpB);
            if (hcpCompare != 0) return hcpCompare;

            return compareHoleByHole(a, b);
        };
    }

    private Comparator<CalculatedScoreData> buildFinalRankingComparator() {
        return (a, b) -> {
            int totalCompare = Integer.compare(b.score.getTotalPoints(), a.score.getTotalPoints());
            if (totalCompare != 0) return totalCompare;

            boolean aDelivered = a.playerData.scorecard.getStatus() == ScorecardStatus.DELIVERED;
            boolean bDelivered = b.playerData.scorecard.getStatus() == ScorecardStatus.DELIVERED;
            if (aDelivered != bDelivered) return aDelivered ? -1 : 1;

            BigDecimal hcpA = a.playerData.handicapIndex != null ? a.playerData.handicapIndex : BigDecimal.valueOf(999);
            BigDecimal hcpB = b.playerData.handicapIndex != null ? b.playerData.handicapIndex : BigDecimal.valueOf(999);
            int hcpCompare = hcpA.compareTo(hcpB);
            if (hcpCompare != 0) return hcpCompare;

            return compareHoleByHole(a.playerData, b.playerData);
        };
    }

    private int compareHoleByHole(PlayerScoreData a, PlayerScoreData b) {
        int startHole = Math.max(a.maxHole, b.maxHole);
        for (int hole = startHole; hole >= 1; hole--) {
            int scoreA = a.scoresByHole.getOrDefault(hole, 99);
            int scoreB = b.scoresByHole.getOrDefault(hole, 99);
            if (scoreA != scoreB) return Integer.compare(scoreA, scoreB);
        }
        return 0;
    }

    private static class PlayerScoreData {
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

    private static class CalculatedScoreData {
        final FrutalesScore score;
        final PlayerScoreData playerData;

        CalculatedScoreData(FrutalesScore score, PlayerScoreData playerData) {
            this.score = score;
            this.playerData = playerData;
        }
    }
}
