package com.golf.tournament.service;

import com.golf.tournament.dto.leaderboard.LeaderboardEntryDTO;
import com.golf.tournament.exception.ResourceNotFoundException;
import com.golf.tournament.model.*;
import com.golf.tournament.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private final TournamentRepository tournamentRepository;
    private final ScorecardRepository scorecardRepository;
    private final HoleScoreRepository holeScoreRepository;
    private final TournamentInscriptionRepository inscriptionRepository;
    private final TournamentCategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public List<LeaderboardEntryDTO> getLeaderboard(Long tournamentId, Long categoryId) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", "id", tournamentId));

        // Get ALL tournament inscriptions instead of just scorecards
        List<TournamentInscription> inscriptions = inscriptionRepository.findByTournamentId(tournamentId);

        List<LeaderboardEntryDTO> entriesWithScores = new ArrayList<>();
        List<LeaderboardEntryDTO> entriesWithoutScores = new ArrayList<>();

        for (TournamentInscription inscription : inscriptions) {
            // Filter by category if specified
            if (categoryId != null && inscription.getCategory() != null &&
                    !inscription.getCategory().getId().equals(categoryId)) {
                continue;
            }

            // Try to find scorecard for this player
            Scorecard scorecard = scorecardRepository
                    .findByTournamentIdAndPlayerId(tournamentId, inscription.getPlayer().getId())
                    .orElse(null);

            Player player = inscription.getPlayer();
            
            // Check if scorecard exists AND is delivered
            if (scorecard != null && Boolean.TRUE.equals(scorecard.getDelivered())) {
                // Player has delivered scorecard - calculate all scores
                List<HoleScore> holeScores = holeScoreRepository.findByScorecardId(scorecard.getId());

                int totalScore = holeScores.stream()
                        .filter(hs -> hs.getGolpesPropio() != null)
                        .mapToInt(HoleScore::getGolpesPropio)
                        .sum();

                int totalPar = holeScores.stream()
                        .mapToInt(hs -> hs.getHole().getPar())
                        .sum();

                BigDecimal handicapCourse = scorecard.getHandicapCourse() != null ? 
                        scorecard.getHandicapCourse() : BigDecimal.ZERO;
                BigDecimal scoreNeto = BigDecimal.valueOf(totalScore).subtract(handicapCourse);
                BigDecimal scoreToPar = scoreNeto.subtract(BigDecimal.valueOf(totalPar));

                LeaderboardEntryDTO entry = LeaderboardEntryDTO.builder()
                        .scorecardId(scorecard.getId())
                        .playerId(player.getId())
                        .inscriptionId(inscription.getId())
                        .playerName(player.getNombre() + " " + player.getApellido())
                        .matricula(player.getMatricula())
                        .clubOrigen(player.getClubOrigen())
                        .categoryName(inscription.getCategory() != null ? inscription.getCategory().getNombre() : null)
                        .scoreGross(totalScore)
                        .scoreNeto(scoreNeto)
                        .totalPar(totalPar)
                        .scoreToPar(scoreToPar)
                        .handicapCourse(handicapCourse)
                        .delivered(true)
                        .pagado(inscription.getPagado() != null ? inscription.getPagado() : false)
                        .build();

                entriesWithScores.add(entry);
            } else {
                // Player has not delivered scorecard or no scorecard exists
                // Show only basic info: player name, matricula, HCP
                BigDecimal handicapCourse = inscription.getHandicapCourse() != null ?
                        inscription.getHandicapCourse() : 
                        (scorecard != null && scorecard.getHandicapCourse() != null ? 
                                scorecard.getHandicapCourse() : BigDecimal.ZERO);

                LeaderboardEntryDTO entry = LeaderboardEntryDTO.builder()
                        .scorecardId(scorecard != null ? scorecard.getId() : null)
                        .playerId(player.getId())
                        .inscriptionId(inscription.getId())
                        .playerName(player.getApellido() + " " + player.getNombre())
                        .matricula(player.getMatricula())
                        .clubOrigen(player.getClubOrigen())
                        .categoryName(null) // No mostrar categoría si no ha entregado
                        .handicapCourse(handicapCourse)
                        .delivered(false)
                        .pagado(inscription.getPagado() != null ? inscription.getPagado() : false)
                        .build();

                entriesWithoutScores.add(entry);
            }
        }

        // Sort players with scores by score neto (lower is better)
        entriesWithScores.sort(Comparator.comparing(LeaderboardEntryDTO::getScoreNeto));

        // Sort players without scores by player name
        entriesWithoutScores.sort(Comparator.comparing(LeaderboardEntryDTO::getPlayerName));

        // Assign positions only to players with delivered scorecards
        AtomicInteger position = new AtomicInteger(1);
        entriesWithScores.forEach(entry -> entry.setPosition(position.getAndIncrement()));

        // Combine lists: first players with scores, then players without scores
        List<LeaderboardEntryDTO> allEntries = new ArrayList<>();
        allEntries.addAll(entriesWithScores);
        allEntries.addAll(entriesWithoutScores);

        return allEntries;
    }

    @Transactional(readOnly = true)
    public List<LeaderboardEntryDTO> getLeaderboardByCategory(Long tournamentId) {
        return getLeaderboard(tournamentId, null);
    }

    @Transactional
    public void updatePayments(Long tournamentId, List<Long> inscriptionIds, List<Boolean> pagadoValues) {
        if (inscriptionIds.size() != pagadoValues.size()) {
            throw new IllegalArgumentException("Las listas de inscriptionIds y pagadoValues deben tener el mismo tamaño");
        }

        for (int i = 0; i < inscriptionIds.size(); i++) {
            Long inscriptionId = inscriptionIds.get(i);
            Boolean pagado = pagadoValues.get(i);

            TournamentInscription inscription = inscriptionRepository.findById(inscriptionId)
                    .orElseThrow(() -> new ResourceNotFoundException("TournamentInscription", "id", inscriptionId));

            // Verificar que la inscripción pertenece al torneo
            if (!inscription.getTournament().getId().equals(tournamentId)) {
                throw new IllegalArgumentException("La inscripción " + inscriptionId + " no pertenece al torneo " + tournamentId);
            }

            inscription.setPagado(pagado);
            inscriptionRepository.save(inscription);
        }
    }
}
