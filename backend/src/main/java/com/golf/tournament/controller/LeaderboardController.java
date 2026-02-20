package com.golf.tournament.controller;

import com.golf.tournament.dto.leaderboard.LeaderboardEntryDTO;
import com.golf.tournament.dto.leaderboard.UpdatePaymentRequest;
import com.golf.tournament.service.LeaderboardService;
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
    private final TournamentService tournamentService;

    @GetMapping("/tournaments/{tournamentId}")
    @PreAuthorize("hasAnyAuthority('TOTAL', 'GAMES')")
    public ResponseEntity<List<LeaderboardEntryDTO>> getLeaderboard(@PathVariable Long tournamentId) {
        // Always return all players with their calculated categoryId
        // Frontend will handle filtering by category
        return ResponseEntity.ok(leaderboardService.getLeaderboard(tournamentId, null));
    }

    /**
     * Public endpoint for viewing tournament results by tournament code.
     * This endpoint is accessible without authentication and returns only
     * information appropriate for public viewing (no payment status).
     */
    @GetMapping("/public/{codigo}")
    public ResponseEntity<List<LeaderboardEntryDTO>> getPublicLeaderboard(@PathVariable String codigo) {
        // Get tournament by code first
        var tournament = tournamentService.getTournamentByCodigo(codigo);
        
        // Return leaderboard for that tournament
        return ResponseEntity.ok(leaderboardService.getLeaderboard(tournament.getId(), null));
    }

    // Deprecated: Kept for backwards compatibility but not used anymore
    @GetMapping("/tournaments/{tournamentId}/categories/{categoryId}")
    @Deprecated
    public ResponseEntity<List<LeaderboardEntryDTO>> getLeaderboardByCategory(
            @PathVariable Long tournamentId,
            @PathVariable Long categoryId) {
        // Return all players - filtering is now done on frontend
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
}
