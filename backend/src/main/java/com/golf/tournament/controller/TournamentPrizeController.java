package com.golf.tournament.controller;

import com.golf.tournament.dto.tournament.TournamentPrizeDTO;
import com.golf.tournament.service.TournamentPrizeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/tournaments/{tournamentId}/prizes")
@RequiredArgsConstructor
public class TournamentPrizeController {

    private final TournamentPrizeService tournamentPrizeService;

    @GetMapping
    public ResponseEntity<List<TournamentPrizeDTO>> getPrizes(@PathVariable Long tournamentId) {
        return ResponseEntity.ok(tournamentPrizeService.getPrizesForTournament(tournamentId));
    }

    @PostMapping("/{prizeType}/winner")
    @PreAuthorize("hasAnyAuthority('TOTAL', 'GAMES')")
    public ResponseEntity<TournamentPrizeDTO> assignWinner(
            @PathVariable Long tournamentId,
            @PathVariable String prizeType,
            @RequestBody Map<String, Long> body) {
        Long inscriptionId = body.get("inscriptionId");
        return ResponseEntity.ok(tournamentPrizeService.assignWinner(tournamentId, prizeType, inscriptionId));
    }

    @DeleteMapping("/{prizeType}/winner")
    @PreAuthorize("hasAnyAuthority('TOTAL', 'GAMES')")
    public ResponseEntity<Void> removeWinner(
            @PathVariable Long tournamentId,
            @PathVariable String prizeType) {
        tournamentPrizeService.removeWinner(tournamentId, prizeType);
        return ResponseEntity.noContent().build();
    }
}
