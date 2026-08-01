package com.golf.tournament.service;

import com.golf.tournament.model.Scorecard;
import com.golf.tournament.model.ScorecardStatus;
import com.golf.tournament.model.Tournament;
import com.golf.tournament.model.TournamentAdminStage;
import com.golf.tournament.repository.ScorecardRepository;
import com.golf.tournament.repository.TournamentAdminRepository;
import com.golf.tournament.repository.TournamentAdminStageRepository;
import com.golf.tournament.repository.TournamentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Scheduler que cierra automáticamente los torneos cuyo horario de cierre fue alcanzado.
 *
 * Lógica de tarjetas al cerrar:
 *  - IN_PROGRESS / PENDING_CONFIG con todos los hoyos cargados → DELIVERED
 *  - IN_PROGRESS / PENDING_CONFIG con carga parcial              → CANCELLED
 *  - CANCELLED                                                   → sin cambios
 *
 * Además, si el torneo pertenece a un Torneo Administrativo, calcula los puntos
 * automáticamente según el tipo (FRUTALES o CLASICO).
 *
 * Se ejecuta cada minuto. Si el servidor estuvo caído y el horario ya pasó,
 * el torneo se detecta y cierra en la primera ejecución post-inicio.
 * También cierra torneos de días anteriores que quedaron en IN_PROGRESS.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TournamentAutoCloseService {

    private final TournamentRepository tournamentRepository;
    private final TournamentAdminRepository tournamentAdminRepository;
    private final TournamentAdminStageRepository stageRepository;
    private final TournamentAdminStageService stageService;
    private final TournamentService tournamentService;
    private final ScorecardRepository scorecardRepository;
    private final FrutalesScoreService frutalesScoreService;
    private final ClasicScoreService clasicScoreService;

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void autoCloseTournaments() {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        List<Tournament> toClose = tournamentRepository.findTournamentsToAutoClose(today, now);

        if (toClose.isEmpty()) return;

        log.info("Cierre automático: {} torneo(s) a procesar", toClose.size());

        for (Tournament tournament : toClose) {
            try {
                resolveScorecardsBeforeClose(tournament);
                tournamentService.finalizeTournament(tournament.getId());
                log.info("Torneo {} finalizado automáticamente", tournament.getId());
                calculateScoresIfBelongsToAdmin(tournament);
            } catch (Exception e) {
                log.error("Error en cierre automático del torneo {}: {}", tournament.getId(), e.getMessage(), e);
            }
        }
    }

    /**
     * Antes de finalizar el torneo, resuelve el estado de cada tarjeta pendiente:
     *  - Tarjeta con todos los hoyos con golpesPropio cargados → DELIVERED
     *  - Tarjeta con carga parcial o sin hoyos                 → CANCELLED (finalizeTournament la cancela igual)
     *
     * Las tarjetas ya DELIVERED o CANCELLED no se tocan.
     */
    private void resolveScorecardsBeforeClose(Tournament tournament) {
        int holesRequired = resolveHolesRequired(tournament);

        List<Scorecard> pending = scorecardRepository.findByTournamentIdAndStatusIn(
                tournament.getId(),
                List.of(ScorecardStatus.IN_PROGRESS, ScorecardStatus.PENDING_CONFIG));

        if (pending.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now();
        int delivered = 0;
        int cancelled = 0;

        for (Scorecard scorecard : pending) {
            if (isComplete(scorecard, holesRequired)) {
                scorecard.setStatus(ScorecardStatus.DELIVERED);
                scorecard.setDeliveredAt(now);
                delivered++;
            } else {
                scorecard.setStatus(ScorecardStatus.CANCELLED);
                if (scorecard.getDeliveredAt() == null) {
                    scorecard.setDeliveredAt(now);
                }
                cancelled++;
            }
        }

        scorecardRepository.saveAll(pending);
        scorecardRepository.flush();
        log.info("Torneo {}: {} tarjeta(s) entregadas, {} canceladas automáticamente",
                tournament.getId(), delivered, cancelled);
    }

    /**
     * Determina si una tarjeta tiene todos los hoyos requeridos con golpesPropio cargados.
     */
    private boolean isComplete(Scorecard scorecard, int holesRequired) {
        if (scorecard.getHoleScores() == null || scorecard.getHoleScores().isEmpty()) {
            return false;
        }
        long filledHoles = scorecard.getHoleScores().stream()
                .filter(hs -> hs.getGolpesPropio() != null)
                .count();
        return filledHoles >= holesRequired;
    }

    /**
     * Determina la cantidad de hoyos requeridos para la tarjeta.
     * Prioridad: cantidadHoyosJuego del torneo; si no está definido, asume 18.
     */
    private int resolveHolesRequired(Tournament tournament) {
        if (tournament.getCantidadHoyosJuego() != null && tournament.getCantidadHoyosJuego() > 0) {
            return tournament.getCantidadHoyosJuego();
        }
        return 18;
    }

    private void calculateScoresIfBelongsToAdmin(Tournament tournament) {
        tournamentAdminRepository.findByTournamentInAnyStage(tournament.getId()).ifPresent(admin -> {
            try {
                String tipo = tournament.getTipo();
                if ("FRUTALES".equals(tipo)) {
                    frutalesScoreService.calculateScores(tournament.getId());
                    log.info("Puntos FRUTALES calculados automáticamente para torneo {}", tournament.getId());
                } else if ("CLASICO".equals(tipo)) {
                    clasicScoreService.calculateScores(tournament.getId());
                    log.info("Puntos CLASICO calculados automáticamente para torneo {}", tournament.getId());
                }
                // Recalcular las etapas que contienen este torneo para actualizar los totales persistidos
                recalculateStagesContaining(tournament.getId(), admin.getId());
            } catch (Exception e) {
                log.error("Error calculando puntos automáticamente para torneo {}: {}",
                        tournament.getId(), e.getMessage(), e);
            }
        });
    }

    private void recalculateStagesContaining(Long tournamentId, Long adminId) {
        List<TournamentAdminStage> stages = stageRepository.findByTournamentId(tournamentId);
        for (TournamentAdminStage stage : stages) {
            try {
                stageService.calculateStageScores(adminId, stage.getId());
                log.info("Etapa {} recalculada automáticamente tras cierre del torneo {}", stage.getId(), tournamentId);
            } catch (Exception e) {
                log.error("Error recalculando etapa {} para torneo {}: {}", stage.getId(), tournamentId, e.getMessage(), e);
            }
        }
    }
}
