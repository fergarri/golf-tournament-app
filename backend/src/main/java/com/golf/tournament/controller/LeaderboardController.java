package com.golf.tournament.controller;

import com.golf.tournament.dto.leaderboard.FrutalesScoreDTO;
import com.golf.tournament.dto.leaderboard.LeaderboardEntryDTO;
import com.golf.tournament.dto.leaderboard.UpdatePaymentRequest;
import com.golf.tournament.service.FrutalesScoreService;
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
    private final FrutalesScoreService frutalesScoreService;
    private final TournamentService tournamentService;

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

    @GetMapping("/tournaments/{tournamentId}/frutales")
    @PreAuthorize("hasAnyAuthority('TOTAL', 'GAMES')")
    public ResponseEntity<List<FrutalesScoreDTO>> getFrutalesLeaderboard(@PathVariable Long tournamentId) {
        return ResponseEntity.ok(frutalesScoreService.getScores(tournamentId));
    }

    @PostMapping("/tournaments/{tournamentId}/frutales/calculate")
    @PreAuthorize("hasAnyAuthority('TOTAL', 'GAMES')")
    public ResponseEntity<List<FrutalesScoreDTO>> calculateFrutalesScores(@PathVariable Long tournamentId) {
        return ResponseEntity.ok(frutalesScoreService.calculateScores(tournamentId));
    }

    @GetMapping("/public/{codigo}/frutales")
    public ResponseEntity<List<FrutalesScoreDTO>> getPublicFrutalesLeaderboard(@PathVariable String codigo) {
        var tournament = tournamentService.getTournamentByCodigo(codigo);
        return ResponseEntity.ok(frutalesScoreService.getScores(tournament.getId()));
    }
}
