package com.golf.tournament.service;

import com.golf.tournament.dto.tournamentadmin.ScoringConfigDTO;
import com.golf.tournament.model.HoleScore;
import com.golf.tournament.model.Scorecard;
import com.golf.tournament.model.ScorecardStatus;
import com.golf.tournament.model.Tournament;
import com.golf.tournament.model.TournamentAdmin;
import com.golf.tournament.model.TournamentInscription;
import com.golf.tournament.repository.HoleScoreRepository;
import com.golf.tournament.repository.ScorecardRepository;
import com.golf.tournament.repository.TournamentAdminRepository;
import com.golf.tournament.repository.TournamentInscriptionRepository;
import com.golf.tournament.repository.TournamentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Calcula los golpes a descontar del HCP Course de una fecha a partir de la fecha
 * inmediatamente anterior del mismo Torneo Administrativo.
 *
 * El descuento no se arrastra: si el jugador no entregó la tarjeta en esa fecha previa,
 * en la siguiente juega con el HCP Course normal.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UnderParDiscountService {

    static final String MODE_ALL_UNDER_PAR = "ALL_UNDER_PAR";
    static final String MODE_FIRST_PLACE = "FIRST_PLACE";

    private final TournamentAdminRepository tournamentAdminRepository;
    private final TournamentRepository tournamentRepository;
    private final TournamentAdminScoringConfigService scoringConfigService;
    private final ScorecardRepository scorecardRepository;
    private final HoleScoreRepository holeScoreRepository;
    private final TournamentInscriptionRepository inscriptionRepository;

    /**
     * Golpes a restar del HCP Course, por jugador, para la fecha indicada.
     * Mapa vacío si la opción está apagada, la fecha no pertenece a un Torneo Administrativo
     * o no hay una fecha previa.
     */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> discountByPlayerId(Long tournamentId) {
        Optional<TournamentAdmin> admin = tournamentAdminRepository.findByTournamentInAnyStage(tournamentId);
        if (admin.isEmpty()) {
            return Map.of();
        }

        ScoringConfigDTO config = scoringConfigService.getOrDefaultByTournamentAdminId(admin.get().getId());
        if (!Boolean.TRUE.equals(config.getDiscountUnderPar())) {
            return Map.of();
        }

        List<Tournament> fechas = tournamentRepository.findByTournamentAdminId(admin.get().getId());
        Tournament current = fechas.stream()
                .filter(t -> t.getId().equals(tournamentId))
                .findFirst()
                .orElse(null);
        if (current == null || current.getFechaInicio() == null) {
            return Map.of();
        }

        Tournament previous = fechas.stream()
                .filter(t -> !t.getId().equals(current.getId()))
                .filter(t -> isBefore(t, current))
                .max(this::compareFechas)
                .orElse(null);
        if (previous == null) {
            return Map.of();
        }

        Map<Long, BigDecimal> discounts = discountsFromPreviousDate(
                previous, config.getUnderParDiscountMode(), config.getTieBreakMode());
        if (!discounts.isEmpty()) {
            log.info("Descuento bajo par para torneo {} desde fecha {}: {} jugador(es)",
                    tournamentId, previous.getId(), discounts.size());
        }
        return discounts;
    }

    public BigDecimal apply(Map<Long, BigDecimal> discounts, Long playerId, BigDecimal baseHandicapCourse) {
        if (baseHandicapCourse == null || discounts == null || discounts.isEmpty()) {
            return baseHandicapCourse;
        }
        BigDecimal strokes = discounts.getOrDefault(playerId, BigDecimal.ZERO);
        if (strokes.signum() <= 0) {
            return baseHandicapCourse;
        }
        return baseHandicapCourse.subtract(strokes);
    }

    private Map<Long, BigDecimal> discountsFromPreviousDate(Tournament previous, String mode, String tieBreakMode) {
        List<Scorecard> delivered = scorecardRepository.findByTournamentIdAndStatus(
                previous.getId(), ScorecardStatus.DELIVERED);
        if (delivered.isEmpty()) {
            return Map.of();
        }

        Map<Long, Long> categoryByPlayer = new HashMap<>();
        if (!"FRUTALES".equals(previous.getTipo())) {
            for (TournamentInscription inscription : inscriptionRepository.findByTournamentId(previous.getId())) {
                if (inscription.getPlayer() != null && inscription.getCategory() != null) {
                    categoryByPlayer.put(inscription.getPlayer().getId(), inscription.getCategory().getId());
                }
            }
        }

        List<PlayedRound> rounds = new ArrayList<>();
        for (Scorecard scorecard : delivered) {
            if (scorecard.getPlayer() == null) {
                continue;
            }
            PlayedRound round = toPlayedRound(scorecard, categoryByPlayer.get(scorecard.getPlayer().getId()));
            if (round != null) {
                rounds.add(round);
            }
        }
        if (rounds.isEmpty()) {
            return Map.of();
        }

        Map<Long, BigDecimal> discounts = new HashMap<>();
        if (MODE_ALL_UNDER_PAR.equals(mode)) {
            for (PlayedRound round : rounds) {
                if (round.underPar.signum() > 0) {
                    discounts.put(round.playerId, round.underPar);
                }
            }
            return discounts;
        }

        if ("FRUTALES".equals(previous.getTipo())) {
            addFirstPlaceUnderPar(discounts, rounds, tieBreakMode);
            return discounts;
        }

        Map<Long, List<PlayedRound>> byCategory = new HashMap<>();
        for (PlayedRound round : rounds) {
            if (round.categoryId == null) {
                continue;
            }
            byCategory.computeIfAbsent(round.categoryId, id -> new ArrayList<>()).add(round);
        }
        for (List<PlayedRound> group : byCategory.values()) {
            // En Clásico el primer puesto de cada categoría se define por neto, no por el desempate de Frutales.
            addFirstPlaceUnderPar(discounts, group, "NETO_HCP_HOLE");
        }
        return discounts;
    }

    /**
     * Solo el primer puesto de la tabla recibe el descuento. Si empatan el neto, se usa el mismo
     * desempate que el ranking. Si después de ese desempate siguen iguales, los dos son primeros.
     */
    private void addFirstPlaceUnderPar(Map<Long, BigDecimal> discounts, List<PlayedRound> group, String tieBreakMode) {
        if (group.isEmpty()) {
            return;
        }
        Comparator<PlayedRound> ranking = rankingComparator(tieBreakMode);
        List<PlayedRound> ranked = new ArrayList<>(group);
        ranked.sort(ranking);
        PlayedRound first = ranked.get(0);
        for (PlayedRound round : ranked) {
            if (ranking.compare(first, round) != 0) {
                break;
            }
            if (round.underPar.signum() > 0) {
                discounts.put(round.playerId, round.underPar);
            }
        }
    }

    private Comparator<PlayedRound> rankingComparator(String tieBreakMode) {
        if ("GROSS_BACK9".equals(tieBreakMode)) {
            return this::compareGrossBack9;
        }
        return this::compareNetoHcpHole;
    }

    /** Mismo orden que la tabla: neto, HCP Index y hoyo por hoyo desde el último. */
    private int compareNetoHcpHole(PlayedRound a, PlayedRound b) {
        int netoCompare = a.neto.compareTo(b.neto);
        if (netoCompare != 0) {
            return netoCompare;
        }
        int hcpCompare = a.handicapIndex.compareTo(b.handicapIndex);
        if (hcpCompare != 0) {
            return hcpCompare;
        }
        return compareHoleByHoleFromLast(a, b);
    }

    /** Mismo desempate de vuelta gross que usa Frutales cuando está configurado. */
    private int compareGrossBack9(PlayedRound a, PlayedRound b) {
        int backNineCompare = Integer.compare(sumHoles(a, 10, 18), sumHoles(b, 10, 18));
        if (backNineCompare != 0) {
            return backNineCompare;
        }
        for (int hole = 18; hole >= 10; hole--) {
            int scoreCompare = Integer.compare(scoreAt(a, hole), scoreAt(b, hole));
            if (scoreCompare != 0) {
                return scoreCompare;
            }
        }
        return compareHoleByHoleFromLast(a, b);
    }

    private int compareHoleByHoleFromLast(PlayedRound a, PlayedRound b) {
        int startHole = Math.max(a.maxHole, b.maxHole);
        for (int hole = startHole; hole >= 1; hole--) {
            int scoreCompare = Integer.compare(scoreAt(a, hole), scoreAt(b, hole));
            if (scoreCompare != 0) {
                return scoreCompare;
            }
        }
        return 0;
    }

    private int sumHoles(PlayedRound round, int from, int to) {
        int sum = 0;
        for (int hole = from; hole <= to; hole++) {
            sum += scoreAt(round, hole);
        }
        return sum;
    }

    private int scoreAt(PlayedRound round, int holeNumber) {
        return round.scoresByHole.getOrDefault(holeNumber, 99);
    }

    private PlayedRound toPlayedRound(Scorecard scorecard, Long categoryId) {
        List<HoleScore> holes = holeScoreRepository.findByScorecardId(scorecard.getId());
        if (holes.isEmpty() || holes.stream().anyMatch(hs -> hs.getGolpesPropio() == null || hs.getHole() == null)) {
            return null;
        }

        int gross = holes.stream().mapToInt(HoleScore::getGolpesPropio).sum();
        int par = holes.stream().mapToInt(hs -> hs.getHole().getPar()).sum();
        BigDecimal handicap = scorecard.getHandicapCourse() != null ? scorecard.getHandicapCourse() : BigDecimal.ZERO;
        BigDecimal neto = BigDecimal.valueOf(gross).subtract(handicap);
        BigDecimal underPar = BigDecimal.valueOf(par).subtract(neto);
        BigDecimal handicapIndex = scorecard.getPlayer().getHandicapIndex() != null
                ? scorecard.getPlayer().getHandicapIndex()
                : BigDecimal.valueOf(999);

        Map<Integer, Integer> scoresByHole = new HashMap<>();
        int maxHole = 9;
        for (HoleScore holeScore : holes) {
            int numeroHoyo = holeScore.getHole().getNumeroHoyo();
            scoresByHole.put(numeroHoyo, holeScore.getGolpesPropio());
            maxHole = Math.max(maxHole, numeroHoyo);
        }

        return new PlayedRound(
                scorecard.getPlayer().getId(), categoryId, neto, underPar, handicapIndex, scoresByHole, maxHole);
    }

    private boolean isBefore(Tournament candidate, Tournament current) {
        return compareFechas(candidate, current) < 0;
    }

    private int compareFechas(Tournament left, Tournament right) {
        if (left.getFechaInicio() == null && right.getFechaInicio() == null) {
            return Long.compare(left.getId(), right.getId());
        }
        if (left.getFechaInicio() == null) {
            return -1;
        }
        if (right.getFechaInicio() == null) {
            return 1;
        }
        int byDate = left.getFechaInicio().compareTo(right.getFechaInicio());
        if (byDate != 0) {
            return byDate;
        }
        return Long.compare(left.getId(), right.getId());
    }

    private record PlayedRound(
            Long playerId,
            Long categoryId,
            BigDecimal neto,
            BigDecimal underPar,
            BigDecimal handicapIndex,
            Map<Integer, Integer> scoresByHole,
            int maxHole) {
    }
}
