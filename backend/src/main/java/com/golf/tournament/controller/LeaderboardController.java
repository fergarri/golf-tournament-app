package com.golf.tournament.controller;

import com.golf.tournament.dto.leaderboard.LeaderboardEntryDTO;
import com.golf.tournament.dto.leaderboard.UpdatePaymentRequest;
import com.golf.tournament.service.LeaderboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/leaderboard")
@RequiredArgsConstructor
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    @GetMapping("/tournaments/{tournamentId}")
    public ResponseEntity<List<LeaderboardEntryDTO>> getLeaderboard(@PathVariable Long tournamentId) {
        return ResponseEntity.ok(leaderboardService.getLeaderboardByCategory(tournamentId));
    }

    @GetMapping("/tournaments/{tournamentId}/categories/{categoryId}")
    public ResponseEntity<List<LeaderboardEntryDTO>> getLeaderboardByCategory(
            @PathVariable Long tournamentId,
            @PathVariable Long categoryId) {
        return ResponseEntity.ok(leaderboardService.getLeaderboard(tournamentId, categoryId));
    }

    @PutMapping("/tournaments/{tournamentId}/payments")
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
