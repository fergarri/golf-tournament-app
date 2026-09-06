package com.golf.tournament.controller;

import com.golf.tournament.dto.tournamentadmin.PlayoffRoundSessionDTO;
import com.golf.tournament.dto.tournamentadmin.StartPlayoffRoundRequest;
import com.golf.tournament.service.TournamentAdminPlayoffMatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tournament-admin/{tournamentAdminId}/stages/playoff-brackets")
@PreAuthorize("hasAnyAuthority('TOTAL', 'ADMINISTRATION')")
@RequiredArgsConstructor
public class TournamentAdminPlayoffMatchController {

    private final TournamentAdminPlayoffMatchService matchService;

    @GetMapping("/{bracketId}/rounds")
    public ResponseEntity<List<PlayoffRoundSessionDTO>> getRoundSessions(
            @PathVariable Long tournamentAdminId,
            @PathVariable Long bracketId) {
        return ResponseEntity.ok(matchService.getRoundSessions(tournamentAdminId, bracketId));
    }

    @PostMapping("/{bracketId}/rounds/{roundNumber}/start")
    public ResponseEntity<PlayoffRoundSessionDTO> startRound(
            @PathVariable Long tournamentAdminId,
            @PathVariable Long bracketId,
            @PathVariable Integer roundNumber,
            @Valid @RequestBody StartPlayoffRoundRequest request) {
        return ResponseEntity.ok(matchService.startRound(tournamentAdminId, bracketId, roundNumber, request));
    }

    @PostMapping("/{bracketId}/rounds/{roundNumber}/reset")
    public ResponseEntity<Void> resetRound(
            @PathVariable Long tournamentAdminId,
            @PathVariable Long bracketId,
            @PathVariable Integer roundNumber) {
        matchService.resetRound(tournamentAdminId, bracketId, roundNumber);
        return ResponseEntity.noContent().build();
    }
}
