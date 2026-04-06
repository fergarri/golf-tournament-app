package com.golf.tournament.service;

import com.golf.tournament.dto.scorecard.HoleScoreDTO;
import com.golf.tournament.dto.scorecard.ScorecardDTO;
import com.golf.tournament.dto.scorecard.UpdateScoreRequest;
import com.golf.tournament.dto.scorecard.ConfigureScorecardRequest;
import com.golf.tournament.exception.BadRequestException;
import com.golf.tournament.exception.ResourceNotFoundException;
import com.golf.tournament.dto.scorecard.UpdateScorecardRequest.HoleScoreUpdate;
import com.golf.tournament.model.*;
import com.golf.tournament.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScorecardService {
    private static final String IN_PROGRESS_EXISTS = "IN_PROGRESS_EXISTS";
    private static final String CONTINUE_EXISTING = "CONTINUE_EXISTING";
    private static final String START_NEW = "START_NEW";
    private static final String CATEGORY_SEX_MALE = "M";
    private static final String CATEGORY_SEX_FEMALE = "F";
    private static final String CATEGORY_SEX_MIXED = "X";

    private final ScorecardRepository scorecardRepository;
    private final TournamentRepository tournamentRepository;
    private final PlayerRepository playerRepository;
    private final HoleRepository holeRepository;
    private final CourseTeeRepository courseTeeRepository;
    private final HoleScoreRepository holeScoreRepository;
    private final TournamentInscriptionRepository inscriptionRepository;
    private final TournamentCategoryRepository categoryRepository;
    private final HandicapConversionRepository handicapConversionRepository;
    private final ScorecardEventService scorecardEventService;

    @Transactional
    public ScorecardDTO getOrCreateScorecard(Long tournamentId, Long playerId) {
        return getOrCreateScorecard(tournamentId, playerId, null);
    }

    @Transactional
    public ScorecardDTO getOrCreateScorecard(Long tournamentId, Long playerId, ConfigureScorecardRequest request) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", "id", tournamentId));

        if ("FINALIZED".equals(tournament.getEstado())) {
            throw new BadRequestException("El torneo ha finalizado. No se puede acceder a la tarjeta.");
        }

        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new ResourceNotFoundException("Player", "id", playerId));

        if (!inscriptionRepository.existsByTournamentIdAndPlayerId(tournamentId, playerId)) {
            throw new BadRequestException("Jugador no inscripto en el torneo");
        }

        if (player.getSexo() == null || player.getSexo().trim().isBlank()) {
            throw new BadRequestException("El jugador no tiene sexo definido. Debe ser M o F.");
        }

        String sexo = player.getSexo().trim().toUpperCase();
        if (!"M".equals(sexo) && !"F".equals(sexo)) {
            throw new BadRequestException("El sexo del jugador es inválido. Debe ser M o F.");
        }

        Scorecard scorecard = scorecardRepository.findByTournamentIdAndPlayerId(tournamentId, playerId)
                .orElse(null);

        boolean tournamentFullyConfigured = isTournamentFullyConfigured(tournament);
        if (scorecard != null
                && scorecard.getStatus() == ScorecardStatus.IN_PROGRESS
                && !tournamentFullyConfigured) {
            String inProgressAction = request != null ? request.getInProgressAction() : null;

            if (inProgressAction == null || inProgressAction.trim().isBlank()) {
                return buildInProgressExistsResponse(scorecard);
            }

            if (CONTINUE_EXISTING.equalsIgnoreCase(inProgressAction.trim())) {
                if (player.getHandicapIndex() != null) {
                    assignCategoryToInscription(tournamentId, playerId, player.getHandicapIndex());
                }
                return convertToDTO(scorecard);
            }

            if (START_NEW.equalsIgnoreCase(inProgressAction.trim())) {
                scorecardRepository.delete(scorecard);
                scorecardRepository.flush();
                scorecard = null;
            } else {
                throw new BadRequestException("Acción inválida para scorecard en progreso.");
            }
        }

        if (scorecard == null) {
            scorecard = createNewScorecard(tournament, player, request, sexo);
        }

        if (scorecard.getStatus() == ScorecardStatus.PENDING_CONFIG) {
            scorecard = completePendingScorecard(scorecard, tournament, player, request, sexo);
        }

        if (player.getHandicapIndex() != null) {
            assignCategoryToInscription(tournamentId, playerId, player.getHandicapIndex());
        }
        return convertToDTO(scorecard);
    }

    private boolean isTournamentFullyConfigured(Tournament tournament) {
        return tournament.getCantidadHoyosJuego() != null
                && tournament.getTeeMasculino() != null
                && tournament.getTeeFemenino() != null;
    }

    private ScorecardDTO buildInProgressExistsResponse(Scorecard scorecard) {
        ScorecardDTO dto = convertToDTO(scorecard);
        dto.setStatus(IN_PROGRESS_EXISTS);
        return dto;
    }

    private void assignCategoryToInscription(Long tournamentId, Long playerId, BigDecimal handicapIndex) {
        TournamentInscription inscription = inscriptionRepository
                .findByTournamentIdAndPlayerId(tournamentId, playerId)
                .orElse(null);
        
        if (inscription == null) {
            log.warn("Inscription not found for player {} in tournament {}", playerId, tournamentId);
            return;
        }
        
        List<TournamentCategory> categories = categoryRepository.findByTournamentId(tournamentId);
        TournamentCategory matchingCategory = findCategoryForHandicap(
                handicapIndex,
                inscription.getPlayer().getSexo(),
                categories
        );
        
        inscription.setCategory(matchingCategory);
        inscriptionRepository.save(inscription);
        
        if (matchingCategory != null) {
            log.debug("Assigned category {} to player {} in tournament {}", 
                     matchingCategory.getNombre(), playerId, tournamentId);
        } else {
            log.debug("No matching category for player {} in tournament {} (handicapIndex: {})", 
                     playerId, tournamentId, handicapIndex);
        }
    }

    private TournamentCategory findCategoryForHandicap(BigDecimal handicapIndex,
                                                       String playerSex,
                                                       List<TournamentCategory> categories) {
        if (handicapIndex == null || categories == null || categories.isEmpty()) {
            return null;
        }

        String normalizedPlayerSex = normalizePlayerSex(playerSex);
        for (TournamentCategory category : categories) {
            if (!categoryAppliesToPlayerSex(category, normalizedPlayerSex)) {
                continue;
            }
            if (handicapIndex.compareTo(category.getHandicapMin()) >= 0 &&
                handicapIndex.compareTo(category.getHandicapMax()) <= 0) {
                return category;
            }
        }

        return null;
    }

    private String normalizePlayerSex(String playerSex) {
        if (playerSex == null || playerSex.trim().isBlank()) {
            return CATEGORY_SEX_MIXED;
        }

        String normalized = playerSex.trim().toUpperCase();
        if (CATEGORY_SEX_MALE.equals(normalized) || CATEGORY_SEX_FEMALE.equals(normalized)) {
            return normalized;
        }
        return CATEGORY_SEX_MIXED;
    }

    private String normalizeCategorySex(String categorySex) {
        if (categorySex == null || categorySex.trim().isBlank()) {
            return CATEGORY_SEX_MIXED;
        }

        String normalized = categorySex.trim().toUpperCase();
        if (CATEGORY_SEX_MALE.equals(normalized)
                || CATEGORY_SEX_FEMALE.equals(normalized)
                || CATEGORY_SEX_MIXED.equals(normalized)) {
            return normalized;
        }
        return CATEGORY_SEX_MIXED;
    }

    private boolean categoryAppliesToPlayerSex(TournamentCategory category, String playerSex) {
        String categorySex = normalizeCategorySex(category.getSexoCategoria());
        if (CATEGORY_SEX_MIXED.equals(categorySex)) {
            return true;
        }
        return categorySex.equals(playerSex);
    }

    @Transactional
    public ScorecardDTO assignMarker(Long scorecardId, Long markerId) {
        Scorecard scorecard = scorecardRepository.findById(scorecardId)
                .orElseThrow(() -> new ResourceNotFoundException("Scorecard", "id", scorecardId));

        Player marker = playerRepository.findById(markerId)
                .orElseThrow(() -> new ResourceNotFoundException("Player", "id", markerId));

        if (!inscriptionRepository.existsByTournamentIdAndPlayerId(
                scorecard.getTournament().getId(), markerId)) {
            throw new BadRequestException("Marcador no inscripto en el torneo");
        }

        if (scorecard.getPlayer().getId().equals(markerId)) {
            throw new BadRequestException("No se puede marcar a uno mismo");
        }

        if (Boolean.TRUE.equals(scorecard.getTournament().getControlCruzado())) {
            List<Scorecard> scorecardsQueMarcanAEsteJugador = scorecardRepository
                    .findByTournamentIdAndMarkerId(scorecard.getTournament().getId(), markerId);
            boolean otroYaLoMarca = scorecardsQueMarcanAEsteJugador.stream()
                    .anyMatch(s -> !s.getId().equals(scorecardId));
            if (otroYaLoMarca) {
                String nombreMarcado = (marker.getNombre() != null ? marker.getNombre().trim() : "")
                        + " "
                        + (marker.getApellido() != null ? marker.getApellido().trim() : "");
                nombreMarcado = nombreMarcado.trim();
                if (nombreMarcado.isEmpty()) {
                    nombreMarcado = "Ese jugador";
                }
                throw new BadRequestException(
                        nombreMarcado + " ya está siendo marcado por otro participante. "
                                + "Seleccione otro jugador a quien marcar los golpes.");
            }
        }

        scorecard.setMarker(marker);
        scorecard = scorecardRepository.save(scorecard);

        log.info("Marker {} assigned to scorecard {}", markerId, scorecardId);
        return convertToDTO(scorecard);
    }

    /**
     * Quita el jugador asignado en "marcar a…", limpia golpes cargados en esa fila y el estado de validación.
     */
    @Transactional
    public ScorecardDTO clearMarker(Long scorecardId) {
        Scorecard scorecard = scorecardRepository.findById(scorecardId)
                .orElseThrow(() -> new ResourceNotFoundException("Scorecard", "id", scorecardId));
        ensureScorecardConfigured(scorecard);

        if (scorecard.getStatus() == ScorecardStatus.DELIVERED) {
            throw new BadRequestException("No se puede quitar el marcador de una tarjeta entregada");
        }
        if (scorecard.getStatus() == ScorecardStatus.CANCELLED) {
            throw new BadRequestException("No se puede quitar el marcador de una tarjeta cancelada");
        }

        if (scorecard.getMarker() == null) {
            return convertToDTO(scorecard);
        }

        scorecard.setMarker(null);
        scorecard.setMarcadorValidado(false);

        for (HoleScore hs : holeScoreRepository.findByScorecardId(scorecardId)) {
            hs.setGolpesMarcador(null);
            hs.setValidado(false);
            holeScoreRepository.save(hs);
        }

        scorecard = scorecardRepository.save(scorecard);

        final Long idParaEvento = scorecard.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                scorecardEventService.notifyConcordanciaActualizada(idParaEvento);
            }
        });

        return convertToDTO(scorecard);
    }

    @Transactional
    public void updateScore(Long scorecardId, UpdateScoreRequest request) {
        Scorecard scorecard = scorecardRepository.findById(scorecardId)
                .orElseThrow(() -> new ResourceNotFoundException("Scorecard", "id", scorecardId));
        ensureScorecardConfigured(scorecard);
        
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

        // Recalcular concordancia entre marcadores
        if ("MARCADOR".equalsIgnoreCase(request.getTipo())) {
            // A actualizó los golpes que marcó para B → recalcular en la tarjeta de A
            recomputeMarkerConcordanceForHole(scorecard, hole);
        } else {
            // B actualizó su propio golpe → recalcular en todas las tarjetas donde B es el marcado
            List<Scorecard> markerScorecards = scorecardRepository
                    .findByTournamentIdAndMarkerId(scorecard.getTournament().getId(), scorecard.getPlayer().getId());
            for (Scorecard markerScorecard : markerScorecards) {
                recomputeMarkerConcordanceForHole(markerScorecard, hole);
            }
        }
    }

    @Transactional
    public ScorecardDTO updateScorecard(Long scorecardId, com.golf.tournament.dto.scorecard.UpdateScorecardRequest request) {
        Scorecard scorecard = scorecardRepository.findById(scorecardId)
                .orElseThrow(() -> new ResourceNotFoundException("Scorecard", "id", scorecardId));
        ensureScorecardConfigured(scorecard);

        for (HoleScoreUpdate holeScoreUpdate : request.getHoleScores()) {
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

            // Recalcular concordancia para este hoyo (puede haber cambiado propio y/o marcador)
            recomputeMarkerConcordanceForHole(scorecard, hole);

            // Si también cambió el propio, actualizar tarjetas donde este jugador es el marcado
            if (holeScoreUpdate.getGolpesPropio() != null) {
                List<Scorecard> markerScorecards = scorecardRepository
                        .findByTournamentIdAndMarkerId(scorecard.getTournament().getId(), scorecard.getPlayer().getId());
                for (Scorecard markerScorecard : markerScorecards) {
                    recomputeMarkerConcordanceForHole(markerScorecard, hole);
                }
            }
        }

        log.info("All scores updated for scorecard {}", scorecardId);
        return convertToDTO(scorecard);
    }

    @Transactional
    public ScorecardDTO deliverScorecard(Long scorecardId) {
        Scorecard scorecard = scorecardRepository.findById(scorecardId)
                .orElseThrow(() -> new ResourceNotFoundException("Scorecard", "id", scorecardId));
        ensureScorecardConfigured(scorecard);

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

        if (Boolean.TRUE.equals(scorecard.getTournament().getControlCruzado())) {
            validateScorecardCrossed(scorecard);
        }

        scorecard.setStatus(ScorecardStatus.DELIVERED);
        scorecard.setDeliveredAt(LocalDateTime.now());
        scorecard = scorecardRepository.save(scorecard);

        log.info("Scorecard delivered: {}", scorecardId);
        return convertToDTO(scorecard);
    }

    private void validateScorecardCrossed(Scorecard scorecard) {
        if (!Boolean.TRUE.equals(scorecard.getMarcadorValidado())) {
            throw new BadRequestException(
                    "No se puede entregar la tarjeta hasta que todos los hoyos del jugador que estás marcando estén validados"
            );
        }

        List<Scorecard> scorecardsQueMeMarcan = scorecardRepository
                .findByTournamentIdAndMarkerId(
                        scorecard.getTournament().getId(),
                        scorecard.getPlayer().getId());

        if (scorecardsQueMeMarcan.isEmpty()) {
            throw new BadRequestException(
                "No se puede entregar la tarjeta hasta que alguien te haya marcado"
            );          
        }

        boolean alguienNoMeValidoTodo = scorecardsQueMeMarcan.stream()
                .anyMatch(s -> !Boolean.TRUE.equals(s.getMarcadorValidado()));
        if (alguienNoMeValidoTodo) {
            throw new BadRequestException(
                    "No se puede entregar la tarjeta hasta que tu marcador haya validado todos tus hoyos"
            );
        }
    }

    @Transactional
    public ScorecardDTO cancelScorecard(Long scorecardId) {
        Scorecard scorecard = scorecardRepository.findById(scorecardId)
                .orElseThrow(() -> new ResourceNotFoundException("Scorecard", "id", scorecardId));
        ensureScorecardConfigured(scorecard);

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

    private Scorecard createNewScorecard(Tournament tournament,
                                         Player player,
                                         ConfigureScorecardRequest request,
                                         String sexo) {
        Integer cantidadHoyosJuego = resolveCantidadHoyosJuego(tournament, request);
        CourseTee selectedTee = resolveSelectedTee(tournament, sexo, request);
        boolean canConfigure = cantidadHoyosJuego != null && selectedTee != null;

        Scorecard scorecard = Scorecard.builder()
                .tournament(tournament)
                .player(player)
                .tee(selectedTee)
                .cantidadHoyosJuego(cantidadHoyosJuego)
                .status(canConfigure ? ScorecardStatus.IN_PROGRESS : ScorecardStatus.PENDING_CONFIG)
                .build();

        if (canConfigure) {
            scorecard.setHandicapCourse(calculateHandicapCourse(selectedTee, cantidadHoyosJuego, player));
        }
        scorecard = scorecardRepository.save(scorecard);
        if (canConfigure) {
            initializeHoleScores(scorecard);
        }
        return scorecard;
    }

    private Scorecard completePendingScorecard(Scorecard scorecard,
                                               Tournament tournament,
                                               Player player,
                                               ConfigureScorecardRequest request,
                                               String sexo) {
        Integer cantidadHoyosJuego = scorecard.getCantidadHoyosJuego() != null
                ? scorecard.getCantidadHoyosJuego()
                : resolveCantidadHoyosJuego(tournament, request);
        CourseTee selectedTee = scorecard.getTee() != null
                ? scorecard.getTee()
                : resolveSelectedTee(tournament, sexo, request);

        if (cantidadHoyosJuego == null || selectedTee == null) {
            return scorecard;
        }

        scorecard.setCantidadHoyosJuego(cantidadHoyosJuego);
        scorecard.setTee(selectedTee);
        scorecard.setHandicapCourse(calculateHandicapCourse(selectedTee, cantidadHoyosJuego, player));
        scorecard.setStatus(ScorecardStatus.IN_PROGRESS);
        scorecard = scorecardRepository.save(scorecard);
        initializeHoleScores(scorecard);
        return scorecard;
    }

    private Integer resolveCantidadHoyosJuego(Tournament tournament, ConfigureScorecardRequest request) {
        Integer cantidadHoyosJuego = tournament.getCantidadHoyosJuego() != null
                ? tournament.getCantidadHoyosJuego()
                : (request != null ? request.getCantidadHoyosJuego() : null);
        if (cantidadHoyosJuego == null) {
            return null;
        }
        if (cantidadHoyosJuego != 9 && cantidadHoyosJuego != 18) {
            throw new BadRequestException("La cantidad de hoyos a jugar debe ser 9 o 18.");
        }
        return cantidadHoyosJuego;
    }

    private CourseTee resolveSelectedTee(Tournament tournament, String sexo, ConfigureScorecardRequest request) {
        CourseTee tournamentTee = "F".equals(sexo) ? tournament.getTeeFemenino() : tournament.getTeeMasculino();
        if (tournamentTee != null) {
            return tournamentTee;
        }

        Long requestedTeeId = request != null ? request.getTeeId() : null;
        if (requestedTeeId == null) {
            return null;
        }
        CourseTee requestedTee = playerSelectedTee(tournament, requestedTeeId);
        if (!Boolean.TRUE.equals(requestedTee.getActive())) {
            throw new BadRequestException("El tee seleccionado no se encuentra activo.");
        }
        return requestedTee;
    }

    private CourseTee playerSelectedTee(Tournament tournament, Long teeId) {
        CourseTee tee = courseTeeRepository.findById(teeId)
                .orElseThrow(() -> new ResourceNotFoundException("CourseTee", "id", teeId));
        if (!Objects.equals(tee.getCourse().getId(), tournament.getCourse().getId())) {
            throw new BadRequestException("El tee seleccionado no pertenece al campo del torneo.");
        }
        return tee;
    }

    private BigDecimal calculateHandicapCourse(CourseTee tee, Integer cantidadHoyosJuego, Player player) {
        if (player.getHandicapIndex() == null) {
            throw new BadRequestException("El jugador no tiene handicap index asignado. Hable con el capitan de cancha");
        }

        HandicapConversion conversion = handicapConversionRepository
                .findByTeeAndHandicapIndex(tee.getId(), player.getHandicapIndex())
                .orElseThrow(() -> new BadRequestException(
                        "No se encontró conversión de handicap para el tee seleccionado y el handicap index del jugador"));

        if (cantidadHoyosJuego == 9) {
            return BigDecimal.valueOf(conversion.getCourseHandicap() / 2.0);
        }
        return BigDecimal.valueOf(conversion.getCourseHandicap());
    }

    private void initializeHoleScores(Scorecard scorecard) {
        if (scorecard.getCantidadHoyosJuego() == null) {
            return;
        }
        holeScoreRepository.deleteAll(holeScoreRepository.findByScorecardId(scorecard.getId()));

        List<Hole> holes = holeRepository.findByCourseIdOrderByNumeroHoyoAsc(scorecard.getTournament().getCourse().getId());
        List<Hole> selectedHoles = holes.stream()
                .filter(hole -> scorecard.getCantidadHoyosJuego() == 18 || hole.getNumeroHoyo() <= 9)
                .collect(Collectors.toCollection(ArrayList::new));
        for (Hole hole : selectedHoles) {
            HoleScore holeScore = HoleScore.builder()
                    .scorecard(scorecard)
                    .hole(hole)
                    .build();
            holeScoreRepository.save(holeScore);
        }
    }

    private void ensureScorecardConfigured(Scorecard scorecard) {
        if (scorecard.getStatus() == ScorecardStatus.PENDING_CONFIG) {
            throw new BadRequestException("Debe completar tee de salida y cantidad de hoyos antes de cargar la tarjeta.");
        }
    }

    /**
     * Recalcula la concordancia entre lo que el marcador (scorecardDelMarcador) cargó para el jugador B
     * (golpesMarcador) y lo que B cargó para sí mismo (golpesPropio en la tarjeta de B) para un hoyo dado.
     * Actualiza el campo `validado` en la fila correspondiente de scorecardDelMarcador y recalcula
     * `marcadorValidado` en la scorecard del marcador.
     */
    private void recomputeMarkerConcordanceForHole(Scorecard scorecardDelMarcador, Hole hole) {
        if (scorecardDelMarcador.getMarker() == null) {
            return;
        }

        HoleScore holeScoreDelMarcador = holeScoreRepository
                .findByScorecardIdAndHoleId(scorecardDelMarcador.getId(), hole.getId())
                .orElse(null);

        if (holeScoreDelMarcador == null) {
            return;
        }

        Integer golpesMarcador = holeScoreDelMarcador.getGolpesMarcador();

        // Buscar la tarjeta del jugador marcado (el que recibe los golpes)
        Scorecard scorecardDelMarcado = scorecardRepository
                .findByTournamentIdAndPlayerId(
                        scorecardDelMarcador.getTournament().getId(),
                        scorecardDelMarcador.getMarker().getId())
                .orElse(null);

        Integer golpesPropioDeLMarcado = null;
        if (scorecardDelMarcado != null) {
            HoleScore holeScoreDelMarcado = holeScoreRepository
                    .findByScorecardIdAndHoleId(scorecardDelMarcado.getId(), hole.getId())
                    .orElse(null);
            if (holeScoreDelMarcado != null) {
                golpesPropioDeLMarcado = holeScoreDelMarcado.getGolpesPropio();
            }
        }

        boolean validado = golpesMarcador != null
                && golpesPropioDeLMarcado != null
                && golpesMarcador.equals(golpesPropioDeLMarcado);

        holeScoreDelMarcador.setValidado(validado);
        holeScoreRepository.save(holeScoreDelMarcador);

        // Recalcular marcadorValidado: true si TODOS los hoyos de esta scorecard tienen validado=true
        List<HoleScore> todosLosHoyos = holeScoreRepository.findByScorecardId(scorecardDelMarcador.getId());
        boolean marcadorValidado = !todosLosHoyos.isEmpty()
                && todosLosHoyos.stream().allMatch(hs -> Boolean.TRUE.equals(hs.getValidado()));
        scorecardDelMarcador.setMarcadorValidado(marcadorValidado);
        scorecardRepository.save(scorecardDelMarcador);

        // Notificar al cliente DESPUÉS del commit para que el GET lea datos ya persistidos
        final Long scorecardIdParaEvento = scorecardDelMarcador.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                scorecardEventService.notifyConcordanciaActualizada(scorecardIdParaEvento);
            }
        });

        log.debug("Concordancia recalculada para scorecard {} hoyo {}: validado={}, marcadorValidado={}",
                scorecardDelMarcador.getId(), hole.getNumeroHoyo(), validado, marcadorValidado);
    }

    private ScorecardDTO convertToDTO(Scorecard scorecard) {
        // Cargar la tarjeta del jugador marcado para calcular estadoConcordancia
        Scorecard scorecardDelMarcado = null;
        if (scorecard.getMarker() != null) {
            scorecardDelMarcado = scorecardRepository
                    .findByTournamentIdAndPlayerId(
                            scorecard.getTournament().getId(),
                            scorecard.getMarker().getId())
                    .orElse(null);
        }

        final Scorecard finalScorecardDelMarcado = scorecardDelMarcado;
        List<HoleScoreDTO> holeScores = holeScoreRepository.findByScorecardId(scorecard.getId()).stream()
                .map(hs -> convertHoleScoreToDTO(hs, finalScorecardDelMarcado))
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
                .teeId(scorecard.getTee() != null ? scorecard.getTee().getId() : null)
                .cantidadHoyosJuego(scorecard.getCantidadHoyosJuego())
                .status(scorecard.getStatus().name())
                .deliveredAt(scorecard.getDeliveredAt())
                .holeScores(holeScores)
                .totalScore(totalScore > 0 ? totalScore : null)
                .totalPar(totalPar)
                .marcadorValidado(scorecard.getMarcadorValidado())
                .build();
    }

    private HoleScoreDTO convertHoleScoreToDTO(HoleScore holeScore, Scorecard scorecardDelMarcado) {
        String estadoConcordancia = "NONE";

        if (holeScore.getGolpesMarcador() != null) {
            if (scorecardDelMarcado != null) {
                Integer golpesPropioDeLMarcado = holeScoreRepository
                        .findByScorecardIdAndHoleId(scorecardDelMarcado.getId(), holeScore.getHole().getId())
                        .map(HoleScore::getGolpesPropio)
                        .orElse(null);

                if (golpesPropioDeLMarcado == null) {
                    estadoConcordancia = "PENDING";
                } else if (holeScore.getGolpesMarcador().equals(golpesPropioDeLMarcado)) {
                    estadoConcordancia = "MATCH";
                } else {
                    estadoConcordancia = "MISMATCH";
                }
            } else {
                estadoConcordancia = "PENDING";
            }
        }

        return HoleScoreDTO.builder()
                .id(holeScore.getId())
                .holeId(holeScore.getHole().getId())
                .numeroHoyo(holeScore.getHole().getNumeroHoyo())
                .par(holeScore.getHole().getPar())
                .golpesPropio(holeScore.getGolpesPropio())
                .golpesMarcador(holeScore.getGolpesMarcador())
                .validado(holeScore.getValidado())
                .estadoConcordancia(estadoConcordancia)
                .build();
    }
}
