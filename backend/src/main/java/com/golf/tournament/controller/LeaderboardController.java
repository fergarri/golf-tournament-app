package com.golf.tournament.controller;

import com.golf.tournament.dto.leaderboard.LeaderboardEntryDTO;
import com.golf.tournament.dto.leaderboard.TournamentScoreDTO;
import com.golf.tournament.dto.leaderboard.UpdatePaymentRequest;
import com.golf.tournament.repository.TournamentAdminRepository;
import com.golf.tournament.service.ClasicScoreService;
import com.golf.tournament.service.FrutalesScoreService;
import com.golf.tournament.service.LeaderboardService;
import com.golf.tournament.service.TournamentAdminPlayoffResultService;
import com.golf.tournament.service.TournamentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/leaderboard")
@RequiredArgsConstructor
public class LeaderboardController {

    private final LeaderboardService leaderboardService;
    private final FrutalesScoreService frutalesScoreService;
    private final ClasicScoreService clasicScoreService;
    private final TournamentService tournamentService;
    private final TournamentAdminRepository tournamentAdminRepository;
    private final TournamentAdminPlayoffResultService playoffResultService;

    @GetMapping("/tournaments/{tournamentId}")
    @PreAuthorize("hasAnyAuthority('TOTAL', 'GAMES')")
    public ResponseEntity<List<LeaderboardEntryDTO>> getLeaderboard(@PathVariable Long tournamentId) {
        return ResponseEntity.ok(leaderboardService.getLeaderboard(tournamentId, null));
    }

    @GetMapping("/public/{codigo}")
    public ResponseEntity<List<LeaderboardEntryDTO>> getPublicLeaderboard(@PathVariable String codigo) {
        var tournament = tournamentService.getTournamentByCodigo(codigo);
        return ResponseEntity.ok(leaderboardService.getLeaderboard(tournament.getId(), null));
    }

    @Deprecated
    @GetMapping("/tournaments/{tournamentId}/categories/{categoryId}")
    public ResponseEntity<List<LeaderboardEntryDTO>> getLeaderboardByCategory(
            @PathVariable Long tournamentId,
            @PathVariable Long categoryId) {
        return ResponseEntity.ok(leaderboardService.getLeaderboard(tournamentId, null));
    }

    @PutMapping("/tournaments/{tournamentId}/payments")
    @PreAuthorize("hasAnyAuthority('TOTAL', 'GAMES')")
    public ResponseEntity<Void> updatePayments(
            @PathVariable Long tournamentId,
            @RequestBody UpdatePaymentRequest request) {

        List<Long> inscriptionIds = request.getPayments().stream()
                .map(UpdatePaymentRequest.PaymentUpdate::getInscriptionId)
                .collect(Collectors.toList());

        List<Boolean> pagadoValues = request.getPayments().stream()
                .map(UpdatePaymentRequest.PaymentUpdate::getPagado)
                .collect(Collectors.toList());

        leaderboardService.updatePayments(tournamentId, inscriptionIds, pagadoValues);
        return ResponseEntity.ok().build();
    }

    // ── Frutales (GLOBAL scores) ────────────────────────────────────────────────

    @GetMapping("/tournaments/{tournamentId}/frutales")
    @PreAuthorize("hasAnyAuthority('TOTAL', 'GAMES')")
    public ResponseEntity<List<TournamentScoreDTO>> getFrutalesLeaderboard(@PathVariable Long tournamentId) {
        return ResponseEntity.ok(frutalesScoreService.getScores(tournamentId));
    }

    @PostMapping("/tournaments/{tournamentId}/frutales/calculate")
    @PreAuthorize("hasAnyAuthority('TOTAL', 'GAMES')")
    public ResponseEntity<List<TournamentScoreDTO>> calculateFrutalesScores(@PathVariable Long tournamentId) {
        List<TournamentScoreDTO> result = frutalesScoreService.calculateScores(tournamentId);
        recalculateStagesIfNeeded(tournamentId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/public/{codigo}/frutales")
    public ResponseEntity<List<TournamentScoreDTO>> getPublicFrutalesLeaderboard(@PathVariable String codigo) {
        var tournament = tournamentService.getTournamentByCodigo(codigo);
        return ResponseEntity.ok(frutalesScoreService.getScores(tournament.getId()));
    }

    // ── Clásico (CATEGORY + SCRATCH scores) ────────────────────────────────────

    @GetMapping("/tournaments/{tournamentId}/clasic")
    @PreAuthorize("hasAnyAuthority('TOTAL', 'GAMES')")
    public ResponseEntity<List<TournamentScoreDTO>> getClasicLeaderboard(@PathVariable Long tournamentId) {
        return ResponseEntity.ok(clasicScoreService.getScores(tournamentId));
    }

    @PostMapping("/tournaments/{tournamentId}/clasic/calculate")
    @PreAuthorize("hasAnyAuthority('TOTAL', 'GAMES')")
    public ResponseEntity<List<TournamentScoreDTO>> calculateClasicScores(@PathVariable Long tournamentId) {
        List<TournamentScoreDTO> result = clasicScoreService.calculateScores(tournamentId);
        recalculateStagesIfNeeded(tournamentId);
        return ResponseEntity.ok(result);
    }

    /**
     * Si el torneo pertenece a un Torneo Administrativo, recalcula todas sus etapas y el playoff
     * (mismo comportamiento que el botón manual "Calcular Puntos" de la vista de etapas).
     */
    private void recalculateStagesIfNeeded(Long tournamentId) {
        tournamentAdminRepository.findByTournamentInAnyStage(tournamentId).ifPresent(admin -> {
            try {
                playoffResultService.calculateResults(admin.getId());
            } catch (Exception e) {
                // No interrumpir la respuesta si falla el recálculo de etapas/playoff
            }
        });
    }

    @GetMapping("/public/{codigo}/clasic")
    public ResponseEntity<List<TournamentScoreDTO>> getPublicClasicLeaderboard(@PathVariable String codigo) {
        var tournament = tournamentService.getTournamentByCodigo(codigo);
        return ResponseEntity.ok(clasicScoreService.getScores(tournament.getId()));
    }
}
