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
import java.util.stream.Collectors;

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

        List<Scorecard> deliveredScorecards = scorecardRepository.findDeliveredScorecardsByTournament(tournamentId);

        List<LeaderboardEntryDTO> entries = new ArrayList<>();

        for (Scorecard scorecard : deliveredScorecards) {
            TournamentInscription inscription = inscriptionRepository
                    .findByTournamentIdAndPlayerId(tournamentId, scorecard.getPlayer().getId())
                    .orElse(null);

            if (inscription == null) {
                continue;
            }

            if (categoryId != null && inscription.getCategory() != null &&
                    !inscription.getCategory().getId().equals(categoryId)) {
                continue;
            }

            List<HoleScore> holeScores = holeScoreRepository.findByScorecardId(scorecard.getId());

            int totalScore = holeScores.stream()
                    .filter(hs -> hs.getGolpesPropio() != null)
                    .mapToInt(HoleScore::getGolpesPropio)
                    .sum();

            int totalPar = holeScores.stream()
                    .mapToInt(hs -> hs.getHole().getPar())
                    .sum();

            // Calculate score neto (gross - handicap course)
            BigDecimal handicapInt = inscription.getPlayer().getHandicapIndex();
            BigDecimal scoreNeto = BigDecimal.valueOf(totalScore).subtract(handicapInt);

            BigDecimal scoreToPar = scoreNeto.subtract(BigDecimal.valueOf(totalPar));

            LeaderboardEntryDTO entry = LeaderboardEntryDTO.builder()
                    .scorecardId(scorecard.getId())
                    .playerId(scorecard.getPlayer().getId())
                    .playerName(scorecard.getPlayer().getNombre() + " " + scorecard.getPlayer().getApellido())
                    .matricula(scorecard.getPlayer().getMatricula())
                    .clubOrigen(scorecard.getPlayer().getClubOrigen())
                    .categoryName(inscription.getCategory() != null ? inscription.getCategory().getNombre() : null)
                    .scoreGross(totalScore)
                    .scoreNeto(scoreNeto)
                    .totalPar(totalPar)
                    .scoreToPar(scoreToPar)
                    .handicapCourse(handicapInt)
                    .build();

            entries.add(entry);
        }

        // Sort by score neto (lower is better)
        entries.sort(Comparator.comparing(LeaderboardEntryDTO::getScoreNeto));

        // Assign positions
        AtomicInteger position = new AtomicInteger(1);
        entries.forEach(entry -> entry.setPosition(position.getAndIncrement()));

        return entries;
    }

    @Transactional(readOnly = true)
    public List<LeaderboardEntryDTO> getLeaderboardByCategory(Long tournamentId) {
        return getLeaderboard(tournamentId, null);
    }
}
