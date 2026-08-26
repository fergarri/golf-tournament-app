package com.golf.tournament.service;

import com.golf.tournament.model.Tournament;
import com.golf.tournament.repository.TournamentAdminRepository;
import com.golf.tournament.repository.TournamentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Scheduler que abre y cierra automáticamente los torneos según sus horarios configurados.
 *
 * Auto-inicio: torneos PENDING cuyo horarioInicio ya fue alcanzado pasan a IN_PROGRESS.
 *
 * Auto-cierre: torneos IN_PROGRESS cuyo horarioCierre ya fue alcanzado se finalizan.
 * La lógica de resolución de tarjetas (completas → DELIVERED, incompletas → CANCELLED)
 * está centralizada en TournamentService.finalizeTournament.
 *
 * Además, si el torneo pertenece a un Torneo Administrativo, calcula los puntos
 * automáticamente según el tipo (FRUTALES o CLASICO).
 *
 * Ambos procesos se ejecutan cada minuto. Si el servidor estuvo caído y el horario ya pasó,
 * el torneo se detecta y procesa en la primera ejecución post-inicio.
 * También procesa torneos de días anteriores que quedaron sin actualizar su estado.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TournamentAutoCloseService {

    private final TournamentRepository tournamentRepository;
    private final TournamentAdminRepository tournamentAdminRepository;
    private final TournamentAdminPlayoffResultService playoffResultService;
    private final TournamentService tournamentService;
    private final FrutalesScoreService frutalesScoreService;
    private final ClasicScoreService clasicScoreService;

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void autoStartTournaments() {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        List<Tournament> toStart = tournamentRepository.findTournamentsToAutoStart(today, now);

        if (toStart.isEmpty()) return;

        log.info("Inicio automático: {} torneo(s) a procesar", toStart.size());

        for (Tournament tournament : toStart) {
            try {
                tournamentService.startTournament(tournament.getId());
                log.info("Torneo {} iniciado automáticamente", tournament.getId());
            } catch (Exception e) {
                log.error("Error en inicio automático del torneo {}: {}", tournament.getId(), e.getMessage(), e);
            }
        }
    }

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
                tournamentService.finalizeTournament(tournament.getId());
                log.info("Torneo {} finalizado automáticamente", tournament.getId());
                calculateScoresIfBelongsToAdmin(tournament);
            } catch (Exception e) {
                log.error("Error en cierre automático del torneo {}: {}", tournament.getId(), e.getMessage(), e);
            }
        }
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
                // Recalcular todas las etapas y el playoff del Torneo Administrativo, igual que el
                // botón manual "Calcular Puntos" (TournamentAdminPlayoffResultService.calculateResults
                // ya recalcula todas las etapas antes de recalcular el playoff).
                playoffResultService.calculateResults(admin.getId());
                log.info("Etapas y playoff recalculados automáticamente para torneo admin {} tras cierre del torneo {}",
                        admin.getId(), tournament.getId());
            } catch (Exception e) {
                log.error("Error calculando puntos automáticamente para torneo {}: {}",
                        tournament.getId(), e.getMessage(), e);
            }
        });
    }
}
