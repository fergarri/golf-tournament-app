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
import java.math.BigDecimal;

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
    private final TournamentCategoryRepository categoryRepository;
    private final HandicapConversionRepository handicapConversionRepository;
    private final CourseTeeRepository courseTeeRepository;

    @Transactional
    public ScorecardDTO getOrCreateScorecard(Long tournamentId, Long playerId, Long teeId) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", "id", tournamentId));

        if ("FINALIZED".equals(tournament.getEstado())) {
            throw new BadRequestException("El torneo ha finalizado. No se puede acceder a la tarjeta.");
        }

        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new ResourceNotFoundException("Player", "id", playerId));

        if (!inscriptionRepository.existsByTournamentIdAndPlayerId(tournamentId, playerId)) {
            throw new BadRequestException("Jugador no inscrito en este torneo");
        }

        if (player.getHandicapIndex() == null) {
            throw new BadRequestException("El jugador no tiene handicap index asignado. Hable con el capitan de cancha");
        }

        courseTeeRepository.findById(teeId)
                .orElseThrow(() -> new ResourceNotFoundException("CourseTee", "id", teeId));

        HandicapConversion conversion = handicapConversionRepository
                .findByTeeAndHandicapIndex(teeId, player.getHandicapIndex())
                .orElseThrow(() -> new BadRequestException(
                    "No se encontró conversión de handicap para el tee seleccionado y el handicap index del jugador"));

        BigDecimal handicapCourse;
        if (tournament.getCourse().getCantidadHoyos() == 9) {
            handicapCourse = BigDecimal.valueOf(conversion.getCourseHandicap() / 2.0);
        } else {
            handicapCourse = BigDecimal.valueOf(conversion.getCourseHandicap());
        }

        Scorecard scorecard = scorecardRepository.findByTournamentIdAndPlayerId(tournamentId, playerId)
                .orElseGet(() -> createNewScorecard(tournament, player, handicapCourse));

        boolean handicapChanged = false;
        if (scorecard.getHandicapCourse() == null || 
            (scorecard.getStatus() == ScorecardStatus.IN_PROGRESS && !scorecard.getHandicapCourse().equals(handicapCourse))) {
            scorecard.setHandicapCourse(handicapCourse);
            scorecard = scorecardRepository.save(scorecard);
            handicapChanged = true;
        }

        if (handicapChanged || scorecard.getHandicapCourse() != null) {
            assignCategoryToInscription(tournamentId, playerId, scorecard.getHandicapCourse());
        }

        return convertToDTO(scorecard);
    }

    private void assignCategoryToInscription(Long tournamentId, Long playerId, BigDecimal handicapCourse) {
        TournamentInscription inscription = inscriptionRepository
                .findByTournamentIdAndPlayerId(tournamentId, playerId)
                .orElse(null);
        
        if (inscription == null) {
            log.warn("Inscription not found for player {} in tournament {}", playerId, tournamentId);
            return;
        }
        
        List<TournamentCategory> categories = categoryRepository.findByTournamentId(tournamentId);
        TournamentCategory matchingCategory = findCategoryForHandicap(handicapCourse, categories);
        
        inscription.setCategory(matchingCategory);
        inscriptionRepository.save(inscription);
        
        if (matchingCategory != null) {
            log.debug("Assigned category {} to player {} in tournament {}", 
                     matchingCategory.getNombre(), playerId, tournamentId);
        } else {
            log.debug("No matching category for player {} in tournament {} (handicap: {})", 
                     playerId, tournamentId, handicapCourse);
        }
    }

    private TournamentCategory findCategoryForHandicap(BigDecimal handicapCourse, 
                                                       List<TournamentCategory> categories) {
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

        if ("FINALIZED".equals(scorecard.getTournament().getEstado())) {
            throw new BadRequestException("Imposible entregar la tarjeta. Torneo cerrado.");
        }

        List<HoleScore> allScores = holeScoreRepository.findByScorecardId(scorecardId);
        if (allScores.isEmpty()) {
            throw new BadRequestException("No se puede entregar la tarjeta sin ninguna puntuación");
        }

        boolean allPlayerScoresFilled = allScores.stream()
                .allMatch(hs -> hs.getGolpesPropio() != null);
        
        if (!allPlayerScoresFilled) {
            throw new BadRequestException("No se puede entregar la tarjeta con hoyos incompletos");
        }

        scorecard.setStatus(ScorecardStatus.DELIVERED);
        scorecard.setDeliveredAt(LocalDateTime.now());
        scorecard = scorecardRepository.save(scorecard);

        log.info("Scorecard delivered: {}", scorecardId);
        return convertToDTO(scorecard);
    }

    @Transactional
    public ScorecardDTO cancelScorecard(Long scorecardId) {
        Scorecard scorecard = scorecardRepository.findById(scorecardId)
                .orElseThrow(() -> new ResourceNotFoundException("Scorecard", "id", scorecardId));

        if (scorecard.getStatus() == ScorecardStatus.DELIVERED) {
            throw new BadRequestException("No se puede cancelar una tarjeta ya entregada");
        }

        if (scorecard.getStatus() == ScorecardStatus.CANCELLED) {
            throw new BadRequestException("La tarjeta ya está cancelada");
        }

        List<HoleScore> allScores = holeScoreRepository.findByScorecardId(scorecardId);
        
        if (!allScores.isEmpty()) {
            boolean allPlayerScoresFilled = allScores.stream()
                    .allMatch(hs -> hs.getGolpesPropio() != null);
            
            if (allPlayerScoresFilled) {
                throw new BadRequestException("No se puede cancelar una tarjeta con todos los hoyos completos. Debe entregarla.");
            }
        }

        scorecard.setStatus(ScorecardStatus.CANCELLED);
        scorecard.setDeliveredAt(LocalDateTime.now());
        scorecard = scorecardRepository.save(scorecard);

        log.info("Tarjeta {} cancelada para jugador {}", scorecardId, scorecard.getPlayer().getId());

        return convertToDTO(scorecard);
    }

    @Transactional
    public ScorecardDTO disqualifyScorecard(Long scorecardId) {
        Scorecard scorecard = scorecardRepository.findById(scorecardId)
                .orElseThrow(() -> new ResourceNotFoundException("Scorecard", "id", scorecardId));

        scorecard.setStatus(ScorecardStatus.DISQUALIFIED);
        scorecard = scorecardRepository.save(scorecard);

        log.info("Scorecard {} disqualified for player {}", scorecardId, scorecard.getPlayer().getId());
        return convertToDTO(scorecard);
    }

    @Transactional
    public ScorecardDTO undoDisqualifyScorecard(Long scorecardId) {
        Scorecard scorecard = scorecardRepository.findById(scorecardId)
                .orElseThrow(() -> new ResourceNotFoundException("Scorecard", "id", scorecardId));

        if (scorecard.getStatus() != ScorecardStatus.DISQUALIFIED) {
            throw new BadRequestException("La tarjeta no está descalificada");
        }

        // Determine previous status based on hole scores
        List<HoleScore> allScores = holeScoreRepository.findByScorecardId(scorecardId);
        boolean allFilled = !allScores.isEmpty() && allScores.stream()
                .allMatch(hs -> hs.getGolpesPropio() != null);

        if (allFilled && scorecard.getDeliveredAt() != null) {
            scorecard.setStatus(ScorecardStatus.DELIVERED);
        } else {
            scorecard.setStatus(ScorecardStatus.IN_PROGRESS);
        }

        scorecard = scorecardRepository.save(scorecard);
        log.info("Scorecard {} un-disqualified for player {}", scorecardId, scorecard.getPlayer().getId());
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

    private Scorecard createNewScorecard(Tournament tournament, Player player, BigDecimal handicapCourse) {
        Scorecard scorecard = Scorecard.builder()
                .tournament(tournament)
                .player(player)
                .handicapCourse(handicapCourse)
                .status(ScorecardStatus.IN_PROGRESS)
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
                .status(scorecard.getStatus().name())
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
