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

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private final TournamentRepository tournamentRepository;
    private final ScorecardRepository scorecardRepository;
    private final HoleScoreRepository holeScoreRepository;
    private final TournamentInscriptionRepository inscriptionRepository;
    private final TournamentCategoryRepository categoryRepository;

    /**
     * Determines the category for a player based on their handicap course.
     * Returns the category if the handicap falls within its range, null otherwise.
     */
    private TournamentCategory determineCategory(BigDecimal handicapCourse, List<TournamentCategory> categories) {
        if (handicapCourse == null || categories == null || categories.isEmpty()) {
            return null;
        }

        for (TournamentCategory category : categories) {
            // Check if handicapCourse is within the category range
            if (handicapCourse.compareTo(category.getHandicapMin()) >= 0 &&
                handicapCourse.compareTo(category.getHandicapMax()) <= 0) {
                return category;
            }
        }

        // No category found for this handicap
        return null;
    }

    @Transactional(readOnly = true)
    public List<LeaderboardEntryDTO> getLeaderboard(Long tournamentId, Long categoryId) {
        // Verify tournament exists
        if (!tournamentRepository.existsById(tournamentId)) {
            throw new ResourceNotFoundException("Tournament", "id", tournamentId);
        }

        // Get ALL tournament categories for calculating player categories
        List<TournamentCategory> allCategories = categoryRepository.findByTournamentId(tournamentId);

        // Get ALL tournament inscriptions instead of just scorecards
        List<TournamentInscription> inscriptions = inscriptionRepository.findByTournamentId(tournamentId);

        List<LeaderboardEntryDTO> entriesWithScores = new ArrayList<>();
        List<LeaderboardEntryDTO> entriesWithoutScores = new ArrayList<>();

        for (TournamentInscription inscription : inscriptions) {
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

                // Calculate category based on handicapCourse
                TournamentCategory calculatedCategory = determineCategory(handicapCourse, allCategories);
                Long calculatedCategoryId = calculatedCategory != null ? calculatedCategory.getId() : null;
                String calculatedCategoryName = calculatedCategory != null ? calculatedCategory.getNombre() : null;

                LeaderboardEntryDTO entry = LeaderboardEntryDTO.builder()
                        .scorecardId(scorecard.getId())
                        .playerId(player.getId())
                        .inscriptionId(inscription.getId())
                        .playerName(player.getNombre() + " " + player.getApellido())
                        .matricula(player.getMatricula())
                        .clubOrigen(player.getClubOrigen())
                        .categoryId(calculatedCategoryId)
                        .categoryName(calculatedCategoryName)
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
                // Use inscription category as fallback
                BigDecimal handicapCourse = inscription.getHandicapCourse() != null ?
                        inscription.getHandicapCourse() : 
                        (scorecard != null && scorecard.getHandicapCourse() != null ? 
                                scorecard.getHandicapCourse() : BigDecimal.ZERO);

                // Use inscription category as fallback
                Long fallbackCategoryId = inscription.getCategory() != null ? inscription.getCategory().getId() : null;
                String fallbackCategoryName = inscription.getCategory() != null ? inscription.getCategory().getNombre() : null;

                LeaderboardEntryDTO entry = LeaderboardEntryDTO.builder()
                        .scorecardId(scorecard != null ? scorecard.getId() : null)
                        .playerId(player.getId())
                        .inscriptionId(inscription.getId())
                        .playerName(player.getApellido() + " " + player.getNombre())
                        .matricula(player.getMatricula())
                        .clubOrigen(player.getClubOrigen())
                        .categoryId(fallbackCategoryId)
                        .categoryName(fallbackCategoryName)
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

        // Do NOT assign positions here - they will be calculated in the frontend
        // based on the selected category filter to ensure proper ranking per category

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
