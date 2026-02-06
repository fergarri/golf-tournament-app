package com.golf.tournament.service;

import com.golf.tournament.dto.scorecard.HoleScoreDTO;
import com.golf.tournament.dto.scorecard.ScorecardDTO;
import com.golf.tournament.dto.scorecard.UpdateScoreRequest;
import com.golf.tournament.exception.BadRequestException;
import com.golf.tournament.exception.ResourceNotFoundException;
import com.golf.tournament.model.*;
import com.golf.tournament.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScorecardService {

    private final ScorecardRepository scorecardRepository;
    private final TournamentRepository tournamentRepository;
    private final PlayerRepository playerRepository;
    private final HoleRepository holeRepository;
    private final HoleScoreRepository holeScoreRepository;
    private final TournamentInscriptionRepository inscriptionRepository;

    @Transactional
    public ScorecardDTO getOrCreateScorecard(Long tournamentId, Long playerId, java.math.BigDecimal handicapCourse) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", "id", tournamentId));

        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new ResourceNotFoundException("Player", "id", playerId));

        if (!inscriptionRepository.existsByTournamentIdAndPlayerId(tournamentId, playerId)) {
            throw new BadRequestException("Jugador no inscrito en este torneo");
        }

        Scorecard scorecard = scorecardRepository.findByTournamentIdAndPlayerId(tournamentId, playerId)
                .orElseGet(() -> createNewScorecard(tournament, player, handicapCourse));

        // Update handicap course if it's different and scorecard not delivered
        if (scorecard.getHandicapCourse() == null || 
            (!scorecard.getDelivered() && !scorecard.getHandicapCourse().equals(handicapCourse))) {
            scorecard.setHandicapCourse(handicapCourse);
            scorecard = scorecardRepository.save(scorecard);
        }

        return convertToDTO(scorecard);
    }

    @Transactional
    public ScorecardDTO assignMarker(Long scorecardId, Long markerId) {
        Scorecard scorecard = scorecardRepository.findById(scorecardId)
                .orElseThrow(() -> new ResourceNotFoundException("Scorecard", "id", scorecardId));

        Player marker = playerRepository.findById(markerId)
                .orElseThrow(() -> new ResourceNotFoundException("Player", "id", markerId));

        if (!inscriptionRepository.existsByTournamentIdAndPlayerId(
                scorecard.getTournament().getId(), markerId)) {
            throw new BadRequestException("Marcador no inscrito en este torneo");
        }

        if (scorecard.getPlayer().getId().equals(markerId)) {
            throw new BadRequestException("No se puede marcar a uno mismo");
        }

        scorecard.setMarker(marker);
        scorecard = scorecardRepository.save(scorecard);

        log.info("Marker {} assigned to scorecard {}", markerId, scorecardId);
        return convertToDTO(scorecard);
    }

    @Transactional
    public void updateScore(Long scorecardId, UpdateScoreRequest request) {
        Scorecard scorecard = scorecardRepository.findById(scorecardId)
                .orElseThrow(() -> new ResourceNotFoundException("Scorecard", "id", scorecardId));

        // Allow editing delivered scorecards (removed validation)
        
        Hole hole = holeRepository.findById(request.getHoleId())
                .orElseThrow(() -> new ResourceNotFoundException("Hole", "id", request.getHoleId()));

        HoleScore holeScore = holeScoreRepository.findByScorecardIdAndHoleId(scorecardId, request.getHoleId())
                .orElseGet(() -> HoleScore.builder()
                        .scorecard(scorecard)
                        .hole(hole)
                        .build());

        if ("PROPIO".equalsIgnoreCase(request.getTipo())) {
            holeScore.setGolpesPropio(request.getGolpes());
        } else if ("MARCADOR".equalsIgnoreCase(request.getTipo())) {
            holeScore.setGolpesMarcador(request.getGolpes());
        } else {
            throw new BadRequestException("Tipo de puntuación inválido. Debe ser PROPIO o MARCADOR");
        }

        holeScoreRepository.save(holeScore);
        log.info("Score updated for scorecard {} hole {}: {} = {}", 
                scorecardId, request.getHoleId(), request.getTipo(), request.getGolpes());
    }

    @Transactional
    public ScorecardDTO updateScorecard(Long scorecardId, com.golf.tournament.dto.scorecard.UpdateScorecardRequest request) {
        Scorecard scorecard = scorecardRepository.findById(scorecardId)
                .orElseThrow(() -> new ResourceNotFoundException("Scorecard", "id", scorecardId));

        // Update all hole scores in a single transaction
        for (com.golf.tournament.dto.scorecard.UpdateScorecardRequest.HoleScoreUpdate holeScoreUpdate : request.getHoleScores()) {
            Hole hole = holeRepository.findById(holeScoreUpdate.getHoleId())
                    .orElseThrow(() -> new ResourceNotFoundException("Hole", "id", holeScoreUpdate.getHoleId()));

            HoleScore holeScore = holeScoreRepository.findByScorecardIdAndHoleId(scorecardId, holeScoreUpdate.getHoleId())
                    .orElseGet(() -> HoleScore.builder()
                            .scorecard(scorecard)
                            .hole(hole)
                            .build());

            if (holeScoreUpdate.getGolpesPropio() != null) {
                holeScore.setGolpesPropio(holeScoreUpdate.getGolpesPropio());
            }
            if (holeScoreUpdate.getGolpesMarcador() != null) {
                holeScore.setGolpesMarcador(holeScoreUpdate.getGolpesMarcador());
            }

            holeScoreRepository.save(holeScore);
        }

        log.info("All scores updated for scorecard {}", scorecardId);
        return convertToDTO(scorecard);
    }

    @Transactional
    public ScorecardDTO deliverScorecard(Long scorecardId) {
        Scorecard scorecard = scorecardRepository.findById(scorecardId)
                .orElseThrow(() -> new ResourceNotFoundException("Scorecard", "id", scorecardId));

        List<HoleScore> allScores = holeScoreRepository.findByScorecardId(scorecardId);
        if (allScores.isEmpty()) {
            throw new BadRequestException("No se puede entregar la tarjeta sin ninguna puntuación");
        }

        // Verify all player scores are filled (golpes_propio is not null for all holes)
        boolean allPlayerScoresFilled = allScores.stream()
                .allMatch(hs -> hs.getGolpesPropio() != null);
        
        if (!allPlayerScoresFilled) {
            throw new BadRequestException("No se puede entregar la tarjeta con hoyos incompletos");
        }

        scorecard.setDelivered(true);
        scorecard.setDeliveredAt(LocalDateTime.now());
        scorecard = scorecardRepository.save(scorecard);

        log.info("Scorecard delivered: {}", scorecardId);
        return convertToDTO(scorecard);
    }

    @Transactional(readOnly = true)
    public List<ScorecardDTO> getTournamentScorecards(Long tournamentId) {
        return scorecardRepository.findByTournamentId(tournamentId).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ScorecardDTO getScorecardById(Long scorecardId) {
        Scorecard scorecard = scorecardRepository.findById(scorecardId)
                .orElseThrow(() -> new ResourceNotFoundException("Scorecard", "id", scorecardId));
        return convertToDTO(scorecard);
    }

    private Scorecard createNewScorecard(Tournament tournament, Player player, java.math.BigDecimal handicapCourse) {
        Scorecard scorecard = Scorecard.builder()
                .tournament(tournament)
                .player(player)
                .handicapCourse(handicapCourse)
                .delivered(false)
                .build();

        scorecard = scorecardRepository.save(scorecard);

        List<Hole> holes = holeRepository.findByCourseIdOrderByNumeroHoyoAsc(tournament.getCourse().getId());
        for (Hole hole : holes) {
            HoleScore holeScore = HoleScore.builder()
                    .scorecard(scorecard)
                    .hole(hole)
                    .build();
            holeScoreRepository.save(holeScore);
        }

        log.info("New scorecard created for tournament {} player {} with handicap course {}", 
                tournament.getId(), player.getId(), handicapCourse);
        return scorecard;
    }

    private ScorecardDTO convertToDTO(Scorecard scorecard) {
        List<HoleScoreDTO> holeScores = holeScoreRepository.findByScorecardId(scorecard.getId()).stream()
                .map(this::convertHoleScoreToDTO)
                .collect(Collectors.toList());

        Integer totalScore = holeScores.stream()
                .filter(hs -> hs.getGolpesPropio() != null)
                .mapToInt(HoleScoreDTO::getGolpesPropio)
                .sum();

        Integer totalPar = holeScores.stream()
                .mapToInt(HoleScoreDTO::getPar)
                .sum();

        return ScorecardDTO.builder()
                .id(scorecard.getId())
                .tournamentId(scorecard.getTournament().getId())
                .playerId(scorecard.getPlayer().getId())
                .playerName(scorecard.getPlayer().getNombre() + " " + scorecard.getPlayer().getApellido())
                .markerId(scorecard.getMarker() != null ? scorecard.getMarker().getId() : null)
                .markerName(scorecard.getMarker() != null ?
                        scorecard.getMarker().getNombre() + " " + scorecard.getMarker().getApellido() : null)
                .handicapCourse(scorecard.getHandicapCourse())
                .delivered(scorecard.getDelivered())
                .deliveredAt(scorecard.getDeliveredAt())
                .holeScores(holeScores)
                .totalScore(totalScore > 0 ? totalScore : null)
                .totalPar(totalPar)
                .build();
    }

    private HoleScoreDTO convertHoleScoreToDTO(HoleScore holeScore) {
        return HoleScoreDTO.builder()
                .id(holeScore.getId())
                .holeId(holeScore.getHole().getId())
                .numeroHoyo(holeScore.getHole().getNumeroHoyo())
                .par(holeScore.getHole().getPar())
                .golpesPropio(holeScore.getGolpesPropio())
                .golpesMarcador(holeScore.getGolpesMarcador())
                .validado(holeScore.isValidado())
                .build();
    }
}
