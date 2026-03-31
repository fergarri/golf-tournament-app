package com.golf.tournament.service;

import com.golf.tournament.dto.tournament.TournamentPrizeDTO;
import com.golf.tournament.exception.BadRequestException;
import com.golf.tournament.exception.ResourceNotFoundException;
import com.golf.tournament.model.*;
import com.golf.tournament.repository.TournamentInscriptionRepository;
import com.golf.tournament.repository.TournamentPrizeRepository;
import com.golf.tournament.repository.TournamentPrizeWinnerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TournamentPrizeService {

    private static final Set<String> VALID_PRIZE_TYPES = Set.of("LONG_DRIVER", "BEST_DRIVER", "BEST_APPROACH");

    private final TournamentPrizeRepository tournamentPrizeRepository;
    private final TournamentPrizeWinnerRepository tournamentPrizeWinnerRepository;
    private final TournamentInscriptionRepository tournamentInscriptionRepository;

    @Transactional(readOnly = true)
    public List<TournamentPrizeDTO> getPrizesForTournament(Long tournamentId) {
        return tournamentPrizeRepository.findByTournamentId(tournamentId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public void syncPrizesForTournament(Tournament tournament, List<String> requestedPrizeTypes) {
        if (requestedPrizeTypes == null) {
            requestedPrizeTypes = List.of();
        }

        List<String> validRequested = requestedPrizeTypes.stream()
                .filter(VALID_PRIZE_TYPES::contains)
                .distinct()
                .collect(Collectors.toList());

        List<TournamentPrize> existingPrizes = tournamentPrizeRepository.findByTournamentId(tournament.getId());
        Set<String> existingTypes = existingPrizes.stream()
                .map(TournamentPrize::getPrizeType)
                .collect(Collectors.toSet());

        // Create new prizes
        for (String prizeType : validRequested) {
            if (!existingTypes.contains(prizeType)) {
                TournamentPrize prize = TournamentPrize.builder()
                        .tournament(tournament)
                        .prizeType(prizeType)
                        .build();
                tournamentPrizeRepository.save(prize);
                log.info("Created prize {} for tournament {}", prizeType, tournament.getId());
            }
        }

        // Delete removed prizes (and their winners via cascade)
        Set<String> requestedSet = Set.copyOf(validRequested);
        for (TournamentPrize existing : existingPrizes) {
            if (!requestedSet.contains(existing.getPrizeType())) {
                tournamentPrizeRepository.delete(existing);
                log.info("Deleted prize {} from tournament {}", existing.getPrizeType(), tournament.getId());
            }
        }
    }

    @Transactional
    public TournamentPrizeDTO assignWinner(Long tournamentId, String prizeType, Long inscriptionId) {
        TournamentPrize prize = tournamentPrizeRepository
                .findByTournamentIdAndPrizeType(tournamentId, prizeType)
                .orElseThrow(() -> new ResourceNotFoundException("TournamentPrize", "prizeType", prizeType));

        TournamentInscription inscription = tournamentInscriptionRepository.findById(inscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("TournamentInscription", "id", inscriptionId));

        if (!inscription.getTournament().getId().equals(tournamentId)) {
            throw new BadRequestException("La inscripción no pertenece a este torneo");
        }

        // Update existing winner or create new one (avoids DELETE+INSERT constraint collision)
        TournamentPrizeWinner winner = tournamentPrizeWinnerRepository.findByPrizeId(prize.getId())
                .orElse(TournamentPrizeWinner.builder().prize(prize).build());
        winner.setInscription(inscription);
        tournamentPrizeWinnerRepository.save(winner);

        log.info("Assigned winner inscription {} for prize {} in tournament {}", inscriptionId, prizeType, tournamentId);

        prize.setWinner(winner);
        return convertToDTO(prize);
    }

    @Transactional
    public void removeWinner(Long tournamentId, String prizeType) {
        TournamentPrize prize = tournamentPrizeRepository
                .findByTournamentIdAndPrizeType(tournamentId, prizeType)
                .orElseThrow(() -> new ResourceNotFoundException("TournamentPrize", "prizeType", prizeType));

        tournamentPrizeWinnerRepository.deleteByPrizeId(prize.getId());
        log.info("Removed winner for prize {} in tournament {}", prizeType, tournamentId);
    }

    public TournamentPrizeDTO convertToDTO(TournamentPrize prize) {
        TournamentPrizeWinner winner = prize.getWinner();
        return TournamentPrizeDTO.builder()
                .id(prize.getId())
                .prizeType(prize.getPrizeType())
                .winnerId(winner != null ? winner.getId() : null)
                .winnerInscriptionId(winner != null ? winner.getInscription().getId() : null)
                .winnerName(winner != null
                        ? winner.getInscription().getPlayer().getNombre() + " " + winner.getInscription().getPlayer().getApellido()
                        : null)
                .build();
    }
}
