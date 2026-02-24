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

    private TournamentCategory determineCategory(BigDecimal handicapCourse, List<TournamentCategory> categories) {
        if (handicapCourse == null || categories == null || categories.isEmpty()) {
            return null;
        }

        for (TournamentCategory category : categories) {
            if (handicapCourse.compareTo(category.getHandicapMin()) >= 0 &&
                handicapCourse.compareTo(category.getHandicapMax()) <= 0) {
                return category;
            }
        }

        return null;
    }

    @Transactional(readOnly = true)
    public List<LeaderboardEntryDTO> getLeaderboard(Long tournamentId, Long categoryId) {
        if (!tournamentRepository.existsById(tournamentId)) {
            throw new ResourceNotFoundException("Tournament", "id", tournamentId);
        }

        List<TournamentCategory> allCategories = categoryRepository.findByTournamentId(tournamentId);
        List<TournamentInscription> inscriptions = inscriptionRepository.findByTournamentId(tournamentId);

        List<LeaderboardEntryDTO> entriesWithScores = new ArrayList<>();
        List<LeaderboardEntryDTO> entriesWithoutScores = new ArrayList<>();

        for (TournamentInscription inscription : inscriptions) {
            Scorecard scorecard = scorecardRepository
                    .findByTournamentIdAndPlayerId(tournamentId, inscription.getPlayer().getId())
                    .orElse(null);

            Player player = inscription.getPlayer();
            
            if (scorecard != null && scorecard.getStatus() == ScorecardStatus.DELIVERED) {
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
                        .status(scorecard.getStatus().name())
                        .pagado(inscription.getPagado() != null ? inscription.getPagado() : false)
                        .build();

                entriesWithScores.add(entry);
            } else {
                BigDecimal handicapCourse = inscription.getHandicapCourse() != null ?
                        inscription.getHandicapCourse() : 
                        (scorecard != null && scorecard.getHandicapCourse() != null ? 
                                scorecard.getHandicapCourse() : BigDecimal.ZERO);

                Long fallbackCategoryId = inscription.getCategory() != null ? inscription.getCategory().getId() : null;
                String fallbackCategoryName = inscription.getCategory() != null ? inscription.getCategory().getNombre() : null;

                String status = scorecard != null ? scorecard.getStatus().name() : ScorecardStatus.IN_PROGRESS.name();

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
                        .status(status)
                        .pagado(inscription.getPagado() != null ? inscription.getPagado() : false)
                        .build();

                entriesWithoutScores.add(entry);
            }
        }

        entriesWithScores.sort(Comparator.comparing(LeaderboardEntryDTO::getScoreNeto));
        entriesWithoutScores.sort(Comparator.comparing(LeaderboardEntryDTO::getPlayerName));

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

            if (!inscription.getTournament().getId().equals(tournamentId)) {
                throw new IllegalArgumentException("La inscripción " + inscriptionId + " no pertenece al torneo " + tournamentId);
            }

            inscription.setPagado(pagado);
            inscriptionRepository.save(inscription);
        }
    }
}
