package com.golf.tournament.service;

import com.golf.tournament.dto.tournamentadmin.ScoringConfigDTO;
import com.golf.tournament.dto.tournamentadmin.PlayoffMatchAccessResponseDTO;
import com.golf.tournament.dto.tournamentadmin.PlayoffMatchStateDTO;
import com.golf.tournament.dto.tournamentadmin.PlayoffRoundSessionDTO;
import com.golf.tournament.dto.tournamentadmin.StartPlayoffRoundRequest;
import com.golf.tournament.dto.tournamentadmin.UpdatePlayoffMatchHolesRequest;
import com.golf.tournament.exception.BadRequestException;
import com.golf.tournament.exception.ResourceNotFoundException;
import com.golf.tournament.model.CourseTee;
import com.golf.tournament.model.HandicapConversion;
import com.golf.tournament.model.Hole;
import com.golf.tournament.model.HoleDistance;
import com.golf.tournament.model.Player;
import com.golf.tournament.model.TournamentAdminPlayoffBracket;
import com.golf.tournament.model.TournamentAdminPlayoffBracketSlot;
import com.golf.tournament.model.TournamentAdminPlayoffMatch;
import com.golf.tournament.model.TournamentAdminPlayoffMatchCard;
import com.golf.tournament.model.TournamentAdminPlayoffMatchHoleScore;
import com.golf.tournament.model.TournamentAdminPlayoffRoundSession;
import com.golf.tournament.repository.CourseTeeRepository;
import com.golf.tournament.repository.HandicapConversionRepository;
import com.golf.tournament.repository.HoleRepository;
import com.golf.tournament.repository.PlayerRepository;
import com.golf.tournament.repository.TournamentAdminPlayoffBracketRepository;
import com.golf.tournament.repository.TournamentAdminPlayoffBracketSlotRepository;
import com.golf.tournament.repository.TournamentAdminPlayoffMatchCardRepository;
import com.golf.tournament.repository.TournamentAdminPlayoffMatchHoleScoreRepository;
import com.golf.tournament.repository.TournamentAdminPlayoffMatchRepository;
import com.golf.tournament.repository.TournamentAdminPlayoffRoundSessionRepository;
import com.golf.tournament.util.MatchPlayPlayingHandicap;
import com.golf.tournament.util.NineHoleCourseHandicapCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TournamentAdminPlayoffMatchService {

    private static final String CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 8;

    private final TournamentAdminPlayoffRoundSessionRepository roundSessionRepository;
    private final TournamentAdminPlayoffMatchRepository matchRepository;
    private final TournamentAdminPlayoffMatchCardRepository matchCardRepository;
    private final TournamentAdminPlayoffMatchHoleScoreRepository holeScoreRepository;
    private final TournamentAdminPlayoffBracketRepository bracketRepository;
    private final TournamentAdminPlayoffBracketSlotRepository slotRepository;
    private final CourseTeeRepository courseTeeRepository;
    private final HoleRepository holeRepository;
    private final HandicapConversionRepository handicapConversionRepository;
    private final PlayerRepository playerRepository;
    private final TournamentAdminPlayoffBracketService bracketService;
    private final TournamentAdminScoringConfigService scoringConfigService;

    private final Random random = new Random();

    // ── Administración ──────────────────────────────────────────────────────

    @Transactional
    public PlayoffRoundSessionDTO startRound(Long tournamentAdminId, Long bracketId, Integer roundNumber,
                                              StartPlayoffRoundRequest request) {
        TournamentAdminPlayoffBracket bracket = getBracketOrThrow(tournamentAdminId, bracketId);
        if (!"CONFIRMED".equals(bracket.getStatus())) {
            throw new BadRequestException("La llave debe estar confirmada para iniciar una ronda de partidos");
        }
        if (roundSessionRepository.findByBracketIdAndRoundNumber(bracketId, roundNumber).isPresent()) {
            throw new BadRequestException(
                    "Ya existe una ronda de partidos iniciada para esta ronda de la llave. " +
                            "Reiniciala primero si necesitás cambiar la configuración.");
        }
        if (request.getCantidadHoyosJuego() == null
                || (request.getCantidadHoyosJuego() != 9 && request.getCantidadHoyosJuego() != 18)) {
            throw new BadRequestException("La cantidad de hoyos a jugar debe ser 9 o 18");
        }

        List<TournamentAdminPlayoffBracketSlot> slots = slotRepository
                .findByBracketIdAndRoundNumberOrderBySlotIndexAsc(bracketId, roundNumber);
        if (slots.isEmpty()) {
            throw new ResourceNotFoundException("Ronda", "roundNumber", roundNumber);
        }

        CourseTee teeMasculino = request.getTeeMasculinoId() != null
                ? courseTeeRepository.findById(request.getTeeMasculinoId())
                        .orElseThrow(() -> new ResourceNotFoundException("CourseTee", "id", request.getTeeMasculinoId()))
                : null;
        CourseTee teeFemenino = request.getTeeFemeninoId() != null
                ? courseTeeRepository.findById(request.getTeeFemeninoId())
                        .orElseThrow(() -> new ResourceNotFoundException("CourseTee", "id", request.getTeeFemeninoId()))
                : null;

        List<Hole> holesInPlay = resolveHolesInPlay(
                bracket.getTournamentAdmin().getCourse().getId(), request.getCantidadHoyosJuego());
        if (holesInPlay.size() < request.getCantidadHoyosJuego()) {
            throw new BadRequestException("La cancha no tiene suficientes hoyos cargados para jugar " +
                    request.getCantidadHoyosJuego() + " hoyos");
        }

        TournamentAdminPlayoffRoundSession session = roundSessionRepository.save(TournamentAdminPlayoffRoundSession.builder()
                .bracket(bracket)
                .roundNumber(roundNumber)
                .code(generateUniqueCode())
                .status("OPEN")
                .teeMasculino(teeMasculino)
                .teeFemenino(teeFemenino)
                .cantidadHoyosJuego(request.getCantidadHoyosJuego())
                .startedAt(LocalDateTime.now())
                .build());

        boolean isHcp = "HCP".equals(bracket.getScoreType());
        int matchesCreated = 0;
        for (int i = 0; i + 1 < slots.size(); i += 2) {
            TournamentAdminPlayoffBracketSlot topSlot = slots.get(i);
            TournamentAdminPlayoffBracketSlot bottomSlot = slots.get(i + 1);
            if (topSlot.getPlayer() == null || bottomSlot.getPlayer() == null) {
                // BYE o rama todavía no alcanzada: se resuelve con el botón manual "Vencedor"
                continue;
            }
            createMatch(session, topSlot, bottomSlot, teeMasculino, teeFemenino, holesInPlay, isHcp);
            matchesCreated++;
        }

        if (matchesCreated == 0) {
            roundSessionRepository.delete(session);
            throw new BadRequestException("Todavía no hay ningún cruce con los dos jugadores definidos en esta ronda.");
        }

        return toRoundSessionDTO(session);
    }

    @Transactional(readOnly = true)
    public List<PlayoffRoundSessionDTO> getRoundSessions(Long tournamentAdminId, Long bracketId) {
        TournamentAdminPlayoffBracket bracket = getBracketOrThrow(tournamentAdminId, bracketId);
        return roundSessionRepository.findByBracketIdOrderByRoundNumberAsc(bracket.getId()).stream()
                .map(this::toRoundSessionDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PlayoffRoundSessionDTO> getPublicRoundSessions(Long tournamentAdminId, Long bracketId) {
        return getRoundSessions(tournamentAdminId, bracketId);
    }

    @Transactional
    public void resetRound(Long tournamentAdminId, Long bracketId, Integer roundNumber) {
        TournamentAdminPlayoffBracket bracket = getBracketOrThrow(tournamentAdminId, bracketId);
        TournamentAdminPlayoffRoundSession session = roundSessionRepository
                .findByBracketIdAndRoundNumber(bracket.getId(), roundNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Ronda", "roundNumber", roundNumber));

        List<TournamentAdminPlayoffMatch> matches = matchRepository.findByRoundSessionId(session.getId());
        List<Long> matchIds = matches.stream().map(TournamentAdminPlayoffMatch::getId).collect(Collectors.toList());
        List<Long> cardIds = matchIds.isEmpty() ? List.of() : matchCardRepository.findByMatchIdIn(matchIds).stream()
                .map(TournamentAdminPlayoffMatchCard::getId)
                .collect(Collectors.toList());

        if (!cardIds.isEmpty() && (holeScoreRepository.existsByCardIdInAndGolpesPropioIsNotNull(cardIds)
                || holeScoreRepository.existsByCardIdInAndHoleResultIsNotNull(cardIds))) {
            throw new BadRequestException(
                    "No se puede reiniciar la ronda: ya hay hoyos cargados en algún partido. " +
                            "Resolvé los partidos en curso primero.");
        }

        roundSessionRepository.delete(session);
    }

    // ── Público ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PlayoffMatchAccessResponseDTO accessByCode(String code, String matricula) {
        TournamentAdminPlayoffRoundSession session = roundSessionRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Código de ronda", "code", code));

        Player player = playerRepository.findByMatricula(matricula.trim())
                .orElseThrow(() -> new BadRequestException("No se encontró ningún jugador con esa matrícula"));

        List<TournamentAdminPlayoffMatch> matches = matchRepository.findByRoundSessionId(session.getId());
        for (TournamentAdminPlayoffMatch match : matches) {
            Player opponent = null;
            if (match.getPlayerA().getId().equals(player.getId())) {
                opponent = match.getPlayerB();
            } else if (match.getPlayerB().getId().equals(player.getId())) {
                opponent = match.getPlayerA();
            }
            if (opponent != null) {
                TournamentAdminPlayoffBracket bracket = session.getBracket();
                return PlayoffMatchAccessResponseDTO.builder()
                        .matchId(match.getId())
                        .tournamentAdminName(bracket.getTournamentAdmin().getNombre())
                        .scoreType(bracket.getScoreType())
                        .roundName(roundNameFor(bracket, session.getRoundNumber()))
                        .playerId(player.getId())
                        .playerName(playerFullName(player))
                        .opponentId(opponent.getId())
                        .opponentName(playerFullName(opponent))
                        .build();
            }
        }
        throw new BadRequestException("No se encontró un partido para esa matrícula en esta ronda");
    }

    @Transactional
    public PlayoffMatchStateDTO getMatchState(String code, Long matchId, String matricula) {
        TournamentAdminPlayoffMatch match = getMatchByCodeOrThrow(code, matchId);
        Player requestingPlayer = resolveRequestingPlayer(match, matricula);
        snapshotAllowanceIfNeeded(match);
        return buildMatchStateDTO(match, requestingPlayer);
    }

    @Transactional
    public PlayoffMatchStateDTO updateHoles(String code, Long matchId, UpdatePlayoffMatchHolesRequest request) {
        TournamentAdminPlayoffMatch match = getMatchByCodeOrThrow(code, matchId);
        Player requestingPlayer = resolveRequestingPlayer(match, request.getMatricula());

        if ("FINISHED".equals(match.getStatus())) {
            throw new BadRequestException("El partido ya terminó, no se puede modificar la tarjeta.");
        }
        snapshotAllowanceIfNeeded(match);

        boolean requestingIsA = match.getPlayerA().getId().equals(requestingPlayer.getId());
        Player opponentPlayer = requestingIsA ? match.getPlayerB() : match.getPlayerA();
        TournamentAdminPlayoffMatchCard ownCard = getCardOrThrow(match.getId(), requestingPlayer.getId());
        TournamentAdminPlayoffMatchCard opponentCard = getCardOrThrow(match.getId(), opponentPlayer.getId());

        if (!"IN_PROGRESS".equals(ownCard.getStatus())) {
            throw new BadRequestException("No se puede modificar la tarjeta: ya está " +
                    ("DELIVERED".equals(ownCard.getStatus()) ? "entregada" : "cancelada"));
        }

        boolean winnerEntry = isWinnerEntry(match);

        int cantidadHoyosJuego = match.getRoundSession().getCantidadHoyosJuego();
        List<Hole> holesInPlay = resolveHolesInPlay(
                match.getRoundSession().getBracket().getTournamentAdmin().getCourse().getId(), cantidadHoyosJuego);

        for (UpdatePlayoffMatchHolesRequest.HoleScoreUpdate update : request.getHoleScores()) {
            Integer seq = update.getHoleSequence();
            if (seq == null || seq < 1) {
                throw new BadRequestException("holeSequence inválido");
            }
            TournamentAdminPlayoffMatchHoleScore ownRow = holeScoreRepository
                    .findByCardIdAndHoleSequence(ownCard.getId(), seq)
                    .orElseGet(() -> createHoleScoreRow(ownCard, seq, holesInPlay, cantidadHoyosJuego));
            TournamentAdminPlayoffMatchHoleScore opponentRow = holeScoreRepository
                    .findByCardIdAndHoleSequence(opponentCard.getId(), seq)
                    .orElseGet(() -> createHoleScoreRow(opponentCard, seq, holesInPlay, cantidadHoyosJuego));

            if (winnerEntry) {
                ownRow.setHoleResult(toAbsoluteHoleResult(update.getHoleResult(), requestingIsA));
                recomputeWinnerValidation(ownRow, opponentRow);
            } else {
                if (update.getGolpesPropio() != null) {
                    ownRow.setGolpesPropio(update.getGolpesPropio());
                }
                if (update.getGolpesRival() != null) {
                    ownRow.setGolpesRival(update.getGolpesRival());
                }
                recomputeValidation(ownRow, opponentRow);
                recomputeValidation(opponentRow, ownRow);
            }
            holeScoreRepository.save(ownRow);
            holeScoreRepository.save(opponentRow);
        }

        return buildMatchStateDTO(match, requestingPlayer);
    }

    @Transactional
    public PlayoffMatchStateDTO deliverMatch(String code, Long matchId, String matricula) {
        TournamentAdminPlayoffMatch match = getMatchByCodeOrThrow(code, matchId);
        Player requestingPlayer = resolveRequestingPlayer(match, matricula);
        TournamentAdminPlayoffMatchCard ownCard = getCardOrThrow(match.getId(), requestingPlayer.getId());

        if ("CANCELLED".equals(ownCard.getStatus())) {
            throw new BadRequestException("No se puede entregar una tarjeta que ya levantó la bola");
        }

        if ("FINISHED".equals(match.getStatus())) {
            // Entrega formal: el partido ya quedó decidido por la otra tarjeta o por abandono.
            if (!"DELIVERED".equals(ownCard.getStatus())) {
                ownCard.setStatus("DELIVERED");
                ownCard.setDeliveredAt(LocalDateTime.now());
                matchCardRepository.save(ownCard);
            }
            return buildMatchStateDTO(match, requestingPlayer);
        }

        snapshotAllowanceIfNeeded(match);
        MatchComputation computation = computeMatch(match);
        TournamentAdminPlayoffMatchCard cardA = getCardOrThrow(match.getId(), match.getPlayerA().getId());
        TournamentAdminPlayoffMatchCard cardB = getCardOrThrow(match.getId(), match.getPlayerB().getId());
        DeliverCheck check = evaluateDeliverEligibility(match, requestingPlayer, computation, cardA, cardB);
        if (!check.eligible) {
            throw new BadRequestException(check.reason);
        }

        boolean winnerIsA = "A".equals(computation.winnerSide);
        Player winner = winnerIsA ? match.getPlayerA() : match.getPlayerB();
        TournamentAdminPlayoffBracketSlot winnerSlot = winnerIsA ? match.getTopSlot() : match.getBottomSlot();

        match.setStatus("FINISHED");
        match.setWinnerPlayer(winner);
        match.setWinnerSlot(winnerSlot);
        match.setResultSummary(computation.resultSummary);
        match.setFinishedAt(LocalDateTime.now());
        matchRepository.save(match);

        ownCard.setStatus("DELIVERED");
        ownCard.setDeliveredAt(LocalDateTime.now());
        matchCardRepository.save(ownCard);

        Long tournamentAdminId = match.getRoundSession().getBracket().getTournamentAdmin().getId();
        bracketService.markWinner(tournamentAdminId, match.getRoundSession().getBracket().getId(), winnerSlot.getId());

        return buildMatchStateDTO(match, requestingPlayer);
    }

    @Transactional
    public PlayoffMatchStateDTO concedeMatch(String code, Long matchId, String matricula) {
        TournamentAdminPlayoffMatch match = getMatchByCodeOrThrow(code, matchId);
        Player requestingPlayer = resolveRequestingPlayer(match, matricula);

        if ("FINISHED".equals(match.getStatus())) {
            throw new BadRequestException("El partido ya terminó");
        }

        boolean requestingIsA = match.getPlayerA().getId().equals(requestingPlayer.getId());
        TournamentAdminPlayoffMatchCard ownCard = getCardOrThrow(match.getId(), requestingPlayer.getId());
        if ("CANCELLED".equals(ownCard.getStatus())) {
            throw new BadRequestException("Ya levantaste la bola en este partido");
        }

        ownCard.setStatus("CANCELLED");
        ownCard.setDeliveredAt(LocalDateTime.now());
        matchCardRepository.save(ownCard);

        Player winner = requestingIsA ? match.getPlayerB() : match.getPlayerA();
        TournamentAdminPlayoffBracketSlot winnerSlot = requestingIsA ? match.getBottomSlot() : match.getTopSlot();

        match.setStatus("FINISHED");
        match.setWinnerPlayer(winner);
        match.setWinnerSlot(winnerSlot);
        match.setResultSummary("Abandono");
        match.setFinishedAt(LocalDateTime.now());
        matchRepository.save(match);

        Long tournamentAdminId = match.getRoundSession().getBracket().getTournamentAdmin().getId();
        bracketService.markWinner(tournamentAdminId, match.getRoundSession().getBracket().getId(), winnerSlot.getId());

        return buildMatchStateDTO(match, requestingPlayer);
    }

    // ── Helpers: creación de partidos ───────────────────────────────────────

    private void createMatch(TournamentAdminPlayoffRoundSession session,
                              TournamentAdminPlayoffBracketSlot topSlot,
                              TournamentAdminPlayoffBracketSlot bottomSlot,
                              CourseTee teeMasculino,
                              CourseTee teeFemenino,
                              List<Hole> holesInPlay,
                              boolean isHcp) {
        Player playerA = topSlot.getPlayer();
        Player playerB = bottomSlot.getPlayer();

        TournamentAdminPlayoffMatch match = matchRepository.save(TournamentAdminPlayoffMatch.builder()
                .roundSession(session)
                .topSlot(topSlot)
                .bottomSlot(bottomSlot)
                .playerA(playerA)
                .playerB(playerB)
                .status("IN_PROGRESS")
                .entryMode("WINNER")
                .build());

        TournamentAdminPlayoffMatchCard cardA = createCard(match, playerA, teeMasculino, teeFemenino,
                session.getCantidadHoyosJuego(), isHcp, holesInPlay);
        TournamentAdminPlayoffMatchCard cardB = createCard(match, playerB, teeMasculino, teeFemenino,
                session.getCantidadHoyosJuego(), isHcp, holesInPlay);

        initializeHoleScores(cardA, holesInPlay);
        initializeHoleScores(cardB, holesInPlay);
    }

    private TournamentAdminPlayoffMatchCard createCard(TournamentAdminPlayoffMatch match, Player player,
                                                        CourseTee teeMasculino, CourseTee teeFemenino,
                                                        int cantidadHoyosJuego, boolean isHcp,
                                                        List<Hole> holesInPlay) {
        String sexo = player.getSexo() != null ? player.getSexo().trim().toUpperCase() : null;
        CourseTee tee = "F".equals(sexo) ? teeFemenino : teeMasculino;
        if (tee == null) {
            throw new BadRequestException("Falta seleccionar el tee de " +
                    ("F".equals(sexo) ? "Damas" : "Caballeros") +
                    " para poder iniciar la ronda (" + playerFullName(player) + ")");
        }

        Integer handicapCourse = null;
        BigDecimal handicapCourseUnrounded = null;
        if (isHcp) {
            if (player.getHandicapIndex() == null) {
                throw new BadRequestException("El jugador " + playerFullName(player) +
                        " no tiene handicap index asignado");
            }
            if (cantidadHoyosJuego == 9) {
                int par9 = holesInPlay.stream().mapToInt(Hole::getPar).sum();
                handicapCourseUnrounded = NineHoleCourseHandicapCalculator.calculateUnrounded(
                        player.getHandicapIndex(), tee.getCourseRatingIda(), tee.getSlopeRatingIda(), par9);
                handicapCourse = NineHoleCourseHandicapCalculator.roundHalfTowardPositiveInfinity(handicapCourseUnrounded);
            } else {
                HandicapConversion conversion = handicapConversionRepository
                        .findByTeeAndHandicapIndex(tee.getId(), player.getHandicapIndex())
                        .orElseThrow(() -> new BadRequestException(
                                "No se encontró conversión de handicap para el tee seleccionado y el handicap index de " +
                                        playerFullName(player)));
                handicapCourse = conversion.getCourseHandicap();
            }
        }

        return matchCardRepository.save(TournamentAdminPlayoffMatchCard.builder()
                .match(match)
                .player(player)
                .tee(tee)
                .handicapCourse(handicapCourse)
                .handicapCourseUnrounded(handicapCourseUnrounded)
                .status("IN_PROGRESS")
                .build());
    }

    private void initializeHoleScores(TournamentAdminPlayoffMatchCard card, List<Hole> holesInPlay) {
        List<TournamentAdminPlayoffMatchHoleScore> rows = new ArrayList<>();
        for (int i = 0; i < holesInPlay.size(); i++) {
            rows.add(TournamentAdminPlayoffMatchHoleScore.builder()
                    .card(card)
                    .holeSequence(i + 1)
                    .hole(holesInPlay.get(i))
                    .validado(false)
                    .build());
        }
        holeScoreRepository.saveAll(rows);
    }

    private TournamentAdminPlayoffMatchHoleScore createHoleScoreRow(TournamentAdminPlayoffMatchCard card,
                                                                     int holeSequence, List<Hole> holesInPlay,
                                                                     int cantidadHoyosJuego) {
        Hole hole = resolveHoleForSequence(holesInPlay, holeSequence, cantidadHoyosJuego);
        return holeScoreRepository.save(TournamentAdminPlayoffMatchHoleScore.builder()
                .card(card)
                .holeSequence(holeSequence)
                .hole(hole)
                .validado(false)
                .build());
    }

    private void recomputeValidation(TournamentAdminPlayoffMatchHoleScore row,
                                      TournamentAdminPlayoffMatchHoleScore counterpart) {
        boolean validado = row.getGolpesRival() != null
                && counterpart.getGolpesPropio() != null
                && row.getGolpesRival().equals(counterpart.getGolpesPropio());
        row.setValidado(validado);
    }

    private void recomputeWinnerValidation(TournamentAdminPlayoffMatchHoleScore own,
                                            TournamentAdminPlayoffMatchHoleScore opponent) {
        boolean validado = own.getHoleResult() != null && own.getHoleResult().equals(opponent.getHoleResult());
        own.setValidado(validado);
        opponent.setValidado(validado);
    }

    private boolean isWinnerEntry(TournamentAdminPlayoffMatch match) {
        return "WINNER".equals(match.getEntryMode());
    }

    /**
     * La primera vez que se abre la tarjeta de un partido nuevo Con HCP, congela el porcentaje
     * vigente de la configuración. Los dos jugadores ven el mismo valor.
     */
    private void snapshotAllowanceIfNeeded(TournamentAdminPlayoffMatch match) {
        if (!isWinnerEntry(match)) {
            return;
        }
        if (!"HCP".equals(match.getRoundSession().getBracket().getScoreType())) {
            return;
        }
        if (match.getHcpAllowancePercent() != null) {
            return;
        }
        Long adminId = match.getRoundSession().getBracket().getTournamentAdmin().getId();
        ScoringConfigDTO config = scoringConfigService.getOrDefaultByTournamentAdminId(adminId);
        BigDecimal percent = config.getMatchPlayHcpPercent() != null
                ? config.getMatchPlayHcpPercent()
                : new BigDecimal("100.0");
        match.setHcpAllowancePercent(percent);
        matchRepository.save(match);
    }

    private String toAbsoluteHoleResult(String relative, boolean requestingIsA) {
        if (relative == null || relative.isBlank()) {
            return null;
        }
        String value = relative.trim().toUpperCase();
        if ("HALVED".equals(value)) {
            return "HALVED";
        }
        if ("ME".equals(value)) {
            return requestingIsA ? "A" : "B";
        }
        if ("OPPONENT".equals(value)) {
            return requestingIsA ? "B" : "A";
        }
        throw new BadRequestException("El resultado del hoyo no es válido");
    }

    private void registerHoleResult(MatchComputation result, int seq, int cantidadHoyosJuego,
                                     String holeWinner, Hole hole) {
        if ("A".equals(holeWinner)) {
            result.holesWonA++;
        } else if ("B".equals(holeWinner)) {
            result.holesWonB++;
        } else {
            result.holesHalved++;
        }
        result.holesPlayed++;

        if (seq <= cantidadHoyosJuego) {
            int diff = result.holesWonA - result.holesWonB;
            int holesRemaining = cantidadHoyosJuego - result.holesPlayed;
            if (Math.abs(diff) > holesRemaining) {
                result.decided = true;
                result.decidedThroughSequence = seq;
                result.winnerSide = diff > 0 ? "A" : "B";
                result.resultSummary = formatMarginResultSummary(Math.abs(diff), holesRemaining);
            }
        } else if ("A".equals(holeWinner) || "B".equals(holeWinner)) {
            result.decided = true;
            result.decidedThroughSequence = seq;
            result.winnerSide = holeWinner;
            int extraHoleNumber = seq - cantidadHoyosJuego;
            result.resultSummary = "Definido en el hoyo extra Nº" + extraHoleNumber
                    + " (hoyo " + hole.getNumeroHoyo() + ")";
        }
    }

    /**
     * El jugador ya marcó la ronda completa, pero el rival no confirmó los mismos hoyos.
     * Hasta que coincidan, el partido no está definido y no se puede entregar.
     */
    private String winnerWaitingReason(Player requestingPlayer, TournamentAdminPlayoffMatch match,
                                        TournamentAdminPlayoffMatchCard cardA,
                                        TournamentAdminPlayoffMatchCard cardB) {
        boolean requestingIsA = match.getPlayerA().getId().equals(requestingPlayer.getId());
        String opponentName = playerFullName(requestingIsA ? match.getPlayerB() : match.getPlayerA());
        Map<Integer, String> marksA = holeResultsBySequence(cardA);
        Map<Integer, String> marksB = holeResultsBySequence(cardB);
        int cantidad = match.getRoundSession().getCantidadHoyosJuego();

        boolean finishedOwnCard = true;
        boolean rivalMissing = false;
        for (int seq = 1; seq <= cantidad; seq++) {
            String mine = requestingIsA ? marksA.get(seq) : marksB.get(seq);
            String theirs = requestingIsA ? marksB.get(seq) : marksA.get(seq);
            if (mine == null) {
                finishedOwnCard = false;
            }
            if (mine != null && theirs == null) {
                rivalMissing = true;
            }
        }
        if (finishedOwnCard && rivalMissing) {
            return opponentName + " todavía no marcó el resultado de los hoyos. "
                    + "Entregar se habilita cuando los dos marquen lo mismo.";
        }
        return null;
    }

    /**
     * @param throughSequence último hoyo que debe coincidir. Los posteriores no se miran.
     *                        Si es {@link Integer#MAX_VALUE}, se revisan todos los hoyos marcados.
     */
    private String winnerDisagreementReason(TournamentAdminPlayoffMatchCard cardA,
                                             TournamentAdminPlayoffMatchCard cardB,
                                             int throughSequence) {
        Map<Integer, String> marksA = holeResultsBySequence(cardA);
        Map<Integer, String> marksB = holeResultsBySequence(cardB);
        int maxSequence = Math.max(
                marksA.keySet().stream().max(Integer::compareTo).orElse(0),
                marksB.keySet().stream().max(Integer::compareTo).orElse(0));
        int limit = Math.min(maxSequence, throughSequence);
        for (int seq = 1; seq <= limit; seq++) {
            String markedA = marksA.get(seq);
            String markedB = marksB.get(seq);
            if (markedA != null && markedB != null && !markedA.equals(markedB)) {
                return "En el hoyo " + seq + " no coincide el ganador que marcaste con el que marcó tu rival.";
            }
        }
        return null;
    }

    private Map<Integer, String> holeResultsBySequence(TournamentAdminPlayoffMatchCard card) {
        Map<Integer, String> result = new HashMap<>();
        for (TournamentAdminPlayoffMatchHoleScore score : holeScoreRepository.findByCardIdOrderByHoleSequenceAsc(card.getId())) {
            if (score.getHoleResult() != null) {
                result.put(score.getHoleSequence(), score.getHoleResult());
            }
        }
        return result;
    }

    // ── Helpers: cálculo del resultado ──────────────────────────────────────

    private static class MatchComputation {
        int holesWonA;
        int holesWonB;
        int holesHalved;
        int holesPlayed;
        boolean decided;
        /** Último hoyo que cuenta para el resultado. Los posteriores no traban la entrega. */
        int decidedThroughSequence;
        String winnerSide;
        String resultSummary;
        Integer playingHandicapA;
        Integer playingHandicapB;
        List<PlayoffMatchStateDTO.HoleInfoDTO> holeInfos = new ArrayList<>();
    }

    private static class DeliverCheck {
        boolean eligible;
        String reason;
    }

    private MatchComputation computeMatch(TournamentAdminPlayoffMatch match) {
        TournamentAdminPlayoffRoundSession session = match.getRoundSession();
        int cantidadHoyosJuego = session.getCantidadHoyosJuego();
        boolean isHcp = "HCP".equals(session.getBracket().getScoreType());

        TournamentAdminPlayoffMatchCard cardA = getCardOrThrow(match.getId(), match.getPlayerA().getId());
        TournamentAdminPlayoffMatchCard cardB = getCardOrThrow(match.getId(), match.getPlayerB().getId());

        Map<Integer, TournamentAdminPlayoffMatchHoleScore> scoresA = holeScoreRepository
                .findByCardIdOrderByHoleSequenceAsc(cardA.getId()).stream()
                .collect(Collectors.toMap(TournamentAdminPlayoffMatchHoleScore::getHoleSequence, s -> s));
        Map<Integer, TournamentAdminPlayoffMatchHoleScore> scoresB = holeScoreRepository
                .findByCardIdOrderByHoleSequenceAsc(cardB.getId()).stream()
                .collect(Collectors.toMap(TournamentAdminPlayoffMatchHoleScore::getHoleSequence, s -> s));

        List<Hole> holesInPlay = resolveHolesInPlay(
                session.getBracket().getTournamentAdmin().getCourse().getId(), cantidadHoyosJuego);

        boolean winnerEntry = isWinnerEntry(match);
        Map<Long, Integer> strokesMap = new HashMap<>();
        boolean aHasMoreHcp = false;
        Integer playingA = null;
        Integer playingB = null;
        if (isHcp && cardA.getHandicapCourse() != null && cardB.getHandicapCourse() != null) {
            if (winnerEntry) {
                BigDecimal percent = match.getHcpAllowancePercent() != null
                        ? match.getHcpAllowancePercent()
                        : new BigDecimal("100");
                playingA = MatchPlayPlayingHandicap.apply(
                        cardA.getHandicapCourse(), cardA.getHandicapCourseUnrounded(), percent);
                playingB = MatchPlayPlayingHandicap.apply(
                        cardB.getHandicapCourse(), cardB.getHandicapCourseUnrounded(), percent);
            } else {
                playingA = cardA.getHandicapCourse();
                playingB = cardB.getHandicapCourse();
            }
            if (!playingA.equals(playingB)) {
                int diff = Math.abs(playingA - playingB);
                aHasMoreHcp = playingA > playingB;
                strokesMap = computeStrokesGivenMap(holesInPlay, diff);
            }
        }

        MatchComputation result = new MatchComputation();
        result.playingHandicapA = playingA;
        result.playingHandicapB = playingB;

        int maxSequence = Math.max(
                scoresA.keySet().stream().max(Integer::compareTo).orElse(0),
                scoresB.keySet().stream().max(Integer::compareTo).orElse(0));

        for (int seq = 1; seq <= maxSequence; seq++) {
            TournamentAdminPlayoffMatchHoleScore scoreA = scoresA.get(seq);
            TournamentAdminPlayoffMatchHoleScore scoreB = scoresB.get(seq);
            Hole hole = resolveHoleForSequence(holesInPlay, seq, cantidadHoyosJuego);

            int strokesA = 0;
            int strokesB = 0;
            if (!strokesMap.isEmpty()) {
                int s = strokesMap.getOrDefault(hole.getId(), 0);
                if (aHasMoreHcp) {
                    strokesA = s;
                } else {
                    strokesB = s;
                }
            }

            String markedA = scoreA != null ? scoreA.getHoleResult() : null;
            String markedB = scoreB != null ? scoreB.getHoleResult() : null;

            String holeWinner = null;
            if (winnerEntry) {
                boolean agreed = markedA != null && markedA.equals(markedB);
                if (!result.decided && agreed) {
                    registerHoleResult(result, seq, cantidadHoyosJuego, markedA, hole);
                    holeWinner = markedA;
                }
            } else {
                boolean bothFilled = scoreA != null && scoreB != null
                        && scoreA.getGolpesPropio() != null && scoreB.getGolpesPropio() != null;
                if (!result.decided && bothFilled) {
                    int netA = scoreA.getGolpesPropio() - strokesA;
                    int netB = scoreB.getGolpesPropio() - strokesB;
                    if (netA < netB) {
                        holeWinner = "A";
                    } else if (netB < netA) {
                        holeWinner = "B";
                    } else {
                        holeWinner = "HALVED";
                    }
                    registerHoleResult(result, seq, cantidadHoyosJuego, holeWinner, hole);
                }
            }

            result.holeInfos.add(PlayoffMatchStateDTO.HoleInfoDTO.builder()
                    .holeSequence(seq)
                    .numeroHoyo(hole.getNumeroHoyo())
                    .par(hole.getPar())
                    .handicapIndex(hole.getHandicap())
                    .distanceA(resolveDistance(hole, cardA.getTee()))
                    .distanceB(resolveDistance(hole, cardB.getTee()))
                    .strokesA(strokesA)
                    .strokesB(strokesB)
                    .golpesPropioA(scoreA != null ? scoreA.getGolpesPropio() : null)
                    .golpesRivalA(scoreA != null ? scoreA.getGolpesRival() : null)
                    .validadoA(scoreA != null ? scoreA.getValidado() : null)
                    .golpesPropioB(scoreB != null ? scoreB.getGolpesPropio() : null)
                    .golpesRivalB(scoreB != null ? scoreB.getGolpesRival() : null)
                    .validadoB(scoreB != null ? scoreB.getValidado() : null)
                    .holeWinner(holeWinner)
                    .markedByA(markedA)
                    .markedByB(markedB)
                    .pending(false)
                    .build());
        }

        // Si terminó la ronda regular empatada (o un ciclo de muerte súbita empatado) y no está
        // decidido, se agrega el próximo hoyo extra como "pending" para que el frontend lo habilite.
        if (!result.decided && result.holesPlayed == maxSequence && result.holesPlayed >= cantidadHoyosJuego) {
            int nextSeq = maxSequence + 1;
            Hole hole = resolveHoleForSequence(holesInPlay, nextSeq, cantidadHoyosJuego);
            int strokesA = 0;
            int strokesB = 0;
            if (!strokesMap.isEmpty()) {
                int s = strokesMap.getOrDefault(hole.getId(), 0);
                if (aHasMoreHcp) {
                    strokesA = s;
                } else {
                    strokesB = s;
                }
            }
            result.holeInfos.add(PlayoffMatchStateDTO.HoleInfoDTO.builder()
                    .holeSequence(nextSeq)
                    .numeroHoyo(hole.getNumeroHoyo())
                    .par(hole.getPar())
                    .handicapIndex(hole.getHandicap())
                    .distanceA(resolveDistance(hole, cardA.getTee()))
                    .distanceB(resolveDistance(hole, cardB.getTee()))
                    .strokesA(strokesA)
                    .strokesB(strokesB)
                    .pending(true)
                    .build());
        }

        return result;
    }

    private DeliverCheck evaluateDeliverEligibility(TournamentAdminPlayoffMatch match, Player requestingPlayer,
                                                     MatchComputation computation,
                                                     TournamentAdminPlayoffMatchCard cardA,
                                                     TournamentAdminPlayoffMatchCard cardB) {
        DeliverCheck check = new DeliverCheck();
        boolean winnerEntry = isWinnerEntry(match);
        if (winnerEntry) {
            int throughSequence = computation.decided
                    ? computation.decidedThroughSequence
                    : Integer.MAX_VALUE;
            String disagreement = winnerDisagreementReason(cardA, cardB, throughSequence);
            if (disagreement != null) {
                check.eligible = false;
                check.reason = disagreement;
                return check;
            }
        }
        if (!computation.decided) {
            check.eligible = false;
            if (winnerEntry) {
                String waiting = winnerWaitingReason(requestingPlayer, match, cardA, cardB);
                if (waiting != null) {
                    check.reason = waiting;
                    return check;
                }
            }
            if (computation.holesPlayed < match.getRoundSession().getCantidadHoyosJuego()) {
                check.reason = winnerEntry
                        ? "Todavía faltan hoyos por marcar para poder entregar la tarjeta."
                        : "Todavía faltan hoyos por cargar para poder entregar la tarjeta.";
            } else {
                check.reason = "El partido está empatado. Hay que seguir jugando hoyos de desempate (muerte súbita).";
            }
            return check;
        }

        if (winnerEntry) {
            check.eligible = true;
            return check;
        }

        boolean requestingIsA = match.getPlayerA().getId().equals(requestingPlayer.getId());
        TournamentAdminPlayoffMatchCard ownCard = requestingIsA ? cardA : cardB;
        TournamentAdminPlayoffMatchCard opponentCard = requestingIsA ? cardB : cardA;

        Map<Integer, TournamentAdminPlayoffMatchHoleScore> ownScores = holeScoreRepository
                .findByCardIdOrderByHoleSequenceAsc(ownCard.getId()).stream()
                .collect(Collectors.toMap(TournamentAdminPlayoffMatchHoleScore::getHoleSequence, s -> s));
        Map<Integer, TournamentAdminPlayoffMatchHoleScore> opponentScores = holeScoreRepository
                .findByCardIdOrderByHoleSequenceAsc(opponentCard.getId()).stream()
                .collect(Collectors.toMap(TournamentAdminPlayoffMatchHoleScore::getHoleSequence, s -> s));

        for (int seq = 1; seq <= computation.holesPlayed; seq++) {
            TournamentAdminPlayoffMatchHoleScore own = ownScores.get(seq);
            TournamentAdminPlayoffMatchHoleScore opponent = opponentScores.get(seq);
            Integer golpesRival = own != null ? own.getGolpesRival() : null;
            Integer opponentPropio = opponent != null ? opponent.getGolpesPropio() : null;
            if (golpesRival == null || opponentPropio == null || !golpesRival.equals(opponentPropio)) {
                check.eligible = false;
                check.reason = "Lo que cargaste sobre tu rival en el hoyo " + seq +
                        " no coincide con lo que tu rival cargó para sí mismo. Revisalo antes de entregar.";
                return check;
            }
        }

        check.eligible = true;
        return check;
    }

    private Map<Long, Integer> computeStrokesGivenMap(List<Hole> holesInPlay, int diffStrokes) {
        Map<Long, Integer> result = new HashMap<>();
        if (diffStrokes <= 0 || holesInPlay.isEmpty()) {
            return result;
        }
        List<Hole> sortedByDifficulty = holesInPlay.stream()
                .sorted(Comparator.comparing(Hole::getHandicap))
                .collect(Collectors.toList());
        int n = sortedByDifficulty.size();
        for (int i = 0; i < diffStrokes; i++) {
            Long holeId = sortedByDifficulty.get(i % n).getId();
            result.merge(holeId, 1, Integer::sum);
        }
        return result;
    }

    private List<Hole> resolveHolesInPlay(Long courseId, int cantidadHoyosJuego) {
        List<Hole> holes = holeRepository.findByCourseIdOrderByNumeroHoyoAsc(courseId);
        return holes.stream()
                .filter(h -> cantidadHoyosJuego == 18 || h.getNumeroHoyo() <= 9)
                .collect(Collectors.toList());
    }

    private Hole resolveHoleForSequence(List<Hole> holesInPlay, int holeSequence, int cantidadHoyosJuego) {
        int idx = (holeSequence - 1) % cantidadHoyosJuego;
        return holesInPlay.get(idx);
    }

    // ── Helpers: DTOs ────────────────────────────────────────────────────────

    private PlayoffRoundSessionDTO toRoundSessionDTO(TournamentAdminPlayoffRoundSession session) {
        List<TournamentAdminPlayoffMatch> matches = matchRepository.findByRoundSessionId(session.getId());
        List<PlayoffRoundSessionDTO.MatchSummaryDTO> matchDTOs = matches.stream()
                .map(this::toMatchSummaryDTO)
                .collect(Collectors.toList());
        return PlayoffRoundSessionDTO.builder()
                .roundSessionId(session.getId())
                .roundNumber(session.getRoundNumber())
                .code(session.getCode())
                .status(session.getStatus())
                .teeMasculinoId(session.getTeeMasculino() != null ? session.getTeeMasculino().getId() : null)
                .teeMasculinoName(session.getTeeMasculino() != null ? session.getTeeMasculino().getNombre() : null)
                .teeFemeninoId(session.getTeeFemenino() != null ? session.getTeeFemenino().getId() : null)
                .teeFemeninoName(session.getTeeFemenino() != null ? session.getTeeFemenino().getNombre() : null)
                .cantidadHoyosJuego(session.getCantidadHoyosJuego())
                .matches(matchDTOs)
                .build();
    }

    private PlayoffRoundSessionDTO.MatchSummaryDTO toMatchSummaryDTO(TournamentAdminPlayoffMatch match) {
        String playerAName = playerFullName(match.getPlayerA());
        String playerBName = playerFullName(match.getPlayerB());

        if ("FINISHED".equals(match.getStatus())) {
            return PlayoffRoundSessionDTO.MatchSummaryDTO.builder()
                    .matchId(match.getId())
                    .topSlotId(match.getTopSlot().getId())
                    .bottomSlotId(match.getBottomSlot().getId())
                    .playerAId(match.getPlayerA().getId())
                    .playerAName(playerAName)
                    .playerBId(match.getPlayerB().getId())
                    .playerBName(playerBName)
                    .status(match.getStatus())
                    .resultSummary(humanizeResultSummary(match.getResultSummary()))
                    .winnerPlayerId(match.getWinnerPlayer() != null ? match.getWinnerPlayer().getId() : null)
                    .build();
        }

        MatchComputation computation = computeMatch(match);
        String liveLabel = buildLiveStatusLabel(computation, playerAName, playerBName,
                match.getRoundSession().getCantidadHoyosJuego());

        return PlayoffRoundSessionDTO.MatchSummaryDTO.builder()
                .matchId(match.getId())
                .topSlotId(match.getTopSlot().getId())
                .bottomSlotId(match.getBottomSlot().getId())
                .playerAId(match.getPlayerA().getId())
                .playerAName(playerAName)
                .playerBId(match.getPlayerB().getId())
                .playerBName(playerBName)
                .status(match.getStatus())
                .holesWonA(computation.holesWonA)
                .holesWonB(computation.holesWonB)
                .holesPlayed(computation.holesPlayed)
                .liveStatusLabel(liveLabel)
                .build();
    }

    private String buildLiveStatusLabel(MatchComputation c, String nameA, String nameB, int cantidadHoyosJuego) {
        if (c.holesPlayed == 0) {
            return "Sin empezar";
        }
        int nextHole = c.holesPlayed + 1;
        String holeLabel = nextHole <= cantidadHoyosJuego
                ? ("hoyo " + nextHole)
                : ("hoyo extra " + (nextHole - cantidadHoyosJuego));
        if (c.holesWonA == c.holesWonB) {
            return "Igualados · " + holeLabel;
        }
        String leaderName = c.holesWonA > c.holesWonB ? nameA : nameB;
        int margin = Math.abs(c.holesWonA - c.holesWonB);
        return leaderName + " " + margin + " arriba · " + holeLabel;
    }

    private PlayoffMatchStateDTO buildMatchStateDTO(TournamentAdminPlayoffMatch match, Player requestingPlayer) {
        TournamentAdminPlayoffMatchCard cardA = getCardOrThrow(match.getId(), match.getPlayerA().getId());
        TournamentAdminPlayoffMatchCard cardB = getCardOrThrow(match.getId(), match.getPlayerB().getId());

        MatchComputation computation = computeMatch(match);

        boolean finished = "FINISHED".equals(match.getStatus());
        Boolean canDeliver;
        String blockedReason = null;
        if (finished) {
            canDeliver = false;
        } else {
            DeliverCheck check = evaluateDeliverEligibility(match, requestingPlayer, computation, cardA, cardB);
            canDeliver = check.eligible;
            blockedReason = check.reason;
        }

        Long leaderPlayerId = null;
        if (computation.holesWonA != computation.holesWonB) {
            leaderPlayerId = computation.holesWonA > computation.holesWonB
                    ? match.getPlayerA().getId() : match.getPlayerB().getId();
        }

        return PlayoffMatchStateDTO.builder()
                .matchId(match.getId())
                .status(match.getStatus())
                .scoreType(match.getRoundSession().getBracket().getScoreType())
                .entryMode(match.getEntryMode() != null ? match.getEntryMode() : "STROKES")
                .hcpAllowancePercent(match.getHcpAllowancePercent())
                .cantidadHoyosJuego(match.getRoundSession().getCantidadHoyosJuego())
                .resultSummary(humanizeResultSummary(match.getResultSummary()))
                .winnerPlayerId(match.getWinnerPlayer() != null ? match.getWinnerPlayer().getId() : null)
                .requestingPlayerId(requestingPlayer.getId())
                .playerA(toPlayerSideDTO(match.getPlayerA(), cardA, computation.playingHandicapA))
                .playerB(toPlayerSideDTO(match.getPlayerB(), cardB, computation.playingHandicapB))
                .holes(computation.holeInfos)
                .tally(PlayoffMatchStateDTO.TallyDTO.builder()
                        .holesWonA(computation.holesWonA)
                        .holesWonB(computation.holesWonB)
                        .holesHalved(computation.holesHalved)
                        .holesPlayed(computation.holesPlayed)
                        .decided(computation.decided)
                        .leaderPlayerId(leaderPlayerId)
                        .margin(Math.abs(computation.holesWonA - computation.holesWonB))
                        .holesRemaining(Math.max(0,
                                match.getRoundSession().getCantidadHoyosJuego() - computation.holesPlayed))
                        .build())
                .canDeliver(canDeliver)
                .blockedReason(blockedReason)
                .build();
    }

    private PlayoffMatchStateDTO.PlayerSideDTO toPlayerSideDTO(Player player, TournamentAdminPlayoffMatchCard card,
                                                                Integer playingHandicap) {
        return PlayoffMatchStateDTO.PlayerSideDTO.builder()
                .playerId(player.getId())
                .playerName(playerFullName(player))
                .shortName(playerShortName(player))
                .teeName(card.getTee() != null ? card.getTee().getNombre() : null)
                .handicapCourse(card.getHandicapCourse())
                .playingHandicap(playingHandicap)
                .cardStatus(card.getStatus())
                .build();
    }

    private static final java.util.regex.Pattern LEGACY_MARGIN_PATTERN =
            java.util.regex.Pattern.compile("^(\\d+)&(\\d+)$");
    private static final java.util.regex.Pattern LEGACY_UP_PATTERN =
            java.util.regex.Pattern.compile("^(\\d+) up$");
    private static final java.util.regex.Pattern LEGACY_EXTRA_HOLE_PATTERN =
            java.util.regex.Pattern.compile("^1 up \\((\\d+) hoyos\\)$");

    /**
     * Convierte al texto amigable actual los resultados que hayan quedado guardados en la BD
     * con el formato viejo de notación de golf (ej: "7&6", "3 up", "1 up (19 hoyos)").
     * Permite que partidos ya finalizados antes de este cambio se vean igual de claros.
     */
    private String humanizeResultSummary(String resultSummary) {
        if (resultSummary == null) return null;
        java.util.regex.Matcher marginMatcher = LEGACY_MARGIN_PATTERN.matcher(resultSummary);
        if (marginMatcher.matches()) {
            return formatMarginResultSummary(
                    Integer.parseInt(marginMatcher.group(1)), Integer.parseInt(marginMatcher.group(2)));
        }
        java.util.regex.Matcher upMatcher = LEGACY_UP_PATTERN.matcher(resultSummary);
        if (upMatcher.matches()) {
            return formatMarginResultSummary(Integer.parseInt(upMatcher.group(1)), 0);
        }
        java.util.regex.Matcher extraMatcher = LEGACY_EXTRA_HOLE_PATTERN.matcher(resultSummary);
        if (extraMatcher.matches()) {
            return "Definido en el hoyo extra (hoyo " + extraMatcher.group(1) + ")";
        }
        return resultSummary;
    }

    /**
     * Texto amigable del resultado final de un partido decidido dentro de la ronda regular
     * (ej: "7 arriba (6 hoyos por jugar)" o "3 arriba" si se definió justo en el último hoyo).
     */
    private String formatMarginResultSummary(int margin, int holesRemaining) {
        String margenTexto = margin + " arriba";
        if (holesRemaining <= 0) {
            return margenTexto;
        }
        return margenTexto + " (" + holesRemaining + (holesRemaining == 1 ? " hoyo por jugar)" : " hoyos por jugar)");
    }

    /** Inicial del nombre + apellido, para usar como título de fila en la tarjeta (ej: "N. Trachta"). */
    private String playerShortName(Player player) {
        String nombre = player.getNombre() != null ? player.getNombre().trim() : "";
        String apellido = player.getApellido() != null ? player.getApellido().trim() : "";
        String inicial = !nombre.isEmpty() ? nombre.substring(0, 1).toUpperCase() + "." : "";
        if (inicial.isEmpty()) return apellido;
        if (apellido.isEmpty()) return inicial;
        return inicial + " " + apellido;
    }

    /** Distancia en yardas de un hoyo para un tee determinado (null si no hay tee o no está cargada). */
    private Integer resolveDistance(Hole hole, CourseTee tee) {
        if (tee == null || hole.getDistances() == null) return null;
        return hole.getDistances().stream()
                .filter(d -> d.getCourseTee() != null && d.getCourseTee().getId().equals(tee.getId()))
                .map(HoleDistance::getDistanciaYardas)
                .findFirst()
                .orElse(null);
    }

    // ── Helpers varios ───────────────────────────────────────────────────────

    private TournamentAdminPlayoffMatch getMatchByCodeOrThrow(String code, Long matchId) {
        TournamentAdminPlayoffRoundSession session = roundSessionRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Código de ronda", "code", code));
        return matchRepository.findByIdAndRoundSessionId(matchId, session.getId())
                .orElseThrow(() -> new ResourceNotFoundException("TournamentAdminPlayoffMatch", "id", matchId));
    }

    private Player resolveRequestingPlayer(TournamentAdminPlayoffMatch match, String matricula) {
        if (matricula == null || matricula.trim().isBlank()) {
            throw new BadRequestException("La matrícula es obligatoria");
        }
        Player player = playerRepository.findByMatricula(matricula.trim())
                .orElseThrow(() -> new BadRequestException("No se encontró ningún jugador con esa matrícula"));
        if (!match.getPlayerA().getId().equals(player.getId()) && !match.getPlayerB().getId().equals(player.getId())) {
            throw new BadRequestException("Ese jugador no participa de este partido");
        }
        return player;
    }

    private TournamentAdminPlayoffMatchCard getCardOrThrow(Long matchId, Long playerId) {
        return matchCardRepository.findByMatchIdAndPlayerId(matchId, playerId)
                .orElseThrow(() -> new ResourceNotFoundException("TournamentAdminPlayoffMatchCard", "playerId", playerId));
    }

    private String playerFullName(Player player) {
        return (player.getNombre() != null ? player.getNombre() : "") + " " +
                (player.getApellido() != null ? player.getApellido() : "");
    }

    private String roundNameFor(TournamentAdminPlayoffBracket bracket, int roundNumber) {
        int totalRounds = log2(bracket.getSize());
        int slotsInRound = bracket.getSize() / (int) Math.pow(2, roundNumber - 1);
        switch (slotsInRound) {
            case 2:
                return "Final";
            case 4:
                return "Semifinal";
            case 8:
                return "Cuartos de Final";
            case 16:
                return "Octavos de Final";
            case 32:
                return "Dieciseisavos de Final";
            default:
                return "Ronda " + roundNumber + " de " + totalRounds;
        }
    }

    private int log2(int size) {
        int rounds = 0;
        int s = size;
        while (s > 1) {
            s /= 2;
            rounds++;
        }
        return rounds;
    }

    private String generateUniqueCode() {
        String code;
        do {
            code = generateRandomCode();
        } while (roundSessionRepository.existsByCode(code));
        return code;
    }

    private String generateRandomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    private TournamentAdminPlayoffBracket getBracketOrThrow(Long tournamentAdminId, Long bracketId) {
        TournamentAdminPlayoffBracket bracket = bracketRepository.findById(bracketId)
                .orElseThrow(() -> new ResourceNotFoundException("TournamentAdminPlayoffBracket", "id", bracketId));
        if (!bracket.getTournamentAdmin().getId().equals(tournamentAdminId)) {
            throw new ResourceNotFoundException("TournamentAdminPlayoffBracket", "id", bracketId);
        }
        return bracket;
    }
}
