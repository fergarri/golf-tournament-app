package com.golf.tournament.controller;

import com.golf.tournament.dto.tournamentadmin.SaveBracketSlotsRequest;
import com.golf.tournament.dto.tournamentadmin.TournamentAdminPlayoffBracketsDTO;
import com.golf.tournament.service.TournamentAdminPlayoffBracketService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tournament-admin/{tournamentAdminId}/stages/playoff-brackets")
@PreAuthorize("hasAnyAuthority('TOTAL', 'ADMINISTRATION')")
@RequiredArgsConstructor
public class TournamentAdminPlayoffBracketController {

    private final TournamentAdminPlayoffBracketService bracketService;

    @GetMapping
    public ResponseEntity<TournamentAdminPlayoffBracketsDTO> getBrackets(@PathVariable Long tournamentAdminId) {
        return ResponseEntity.ok(bracketService.getBrackets(tournamentAdminId));
    }

    @PostMapping("/generate")
    public ResponseEntity<TournamentAdminPlayoffBracketsDTO> generate(
            @PathVariable Long tournamentAdminId,
            @RequestParam(required = false) String scoreType) {
        return ResponseEntity.ok(bracketService.generate(tournamentAdminId, scoreType));
    }

    @PutMapping("/{bracketId}/slots")
    public ResponseEntity<TournamentAdminPlayoffBracketsDTO> saveSlots(
            @PathVariable Long tournamentAdminId,
            @PathVariable Long bracketId,
            @Valid @RequestBody SaveBracketSlotsRequest request) {
        return ResponseEntity.ok(bracketService.saveSlots(tournamentAdminId, bracketId, request));
    }

    @PostMapping("/{bracketId}/confirm")
    public ResponseEntity<TournamentAdminPlayoffBracketsDTO> confirm(
            @PathVariable Long tournamentAdminId,
            @PathVariable Long bracketId) {
        return ResponseEntity.ok(bracketService.confirm(tournamentAdminId, bracketId));
    }

    @PostMapping("/{bracketId}/revert")
    public ResponseEntity<TournamentAdminPlayoffBracketsDTO> revert(
            @PathVariable Long tournamentAdminId,
            @PathVariable Long bracketId) {
        return ResponseEntity.ok(bracketService.revert(tournamentAdminId, bracketId));
    }

    @PostMapping("/{bracketId}/reset")
    public ResponseEntity<TournamentAdminPlayoffBracketsDTO> reset(
            @PathVariable Long tournamentAdminId,
            @PathVariable Long bracketId) {
        return ResponseEntity.ok(bracketService.reset(tournamentAdminId, bracketId));
    }

    @PutMapping("/{bracketId}/slots/{slotId}/winner")
    public ResponseEntity<TournamentAdminPlayoffBracketsDTO> markWinner(
            @PathVariable Long tournamentAdminId,
            @PathVariable Long bracketId,
            @PathVariable Long slotId) {
        return ResponseEntity.ok(bracketService.markWinner(tournamentAdminId, bracketId, slotId));
    }

    @DeleteMapping("/{bracketId}/slots/{slotId}/winner")
    public ResponseEntity<TournamentAdminPlayoffBracketsDTO> undoWinner(
            @PathVariable Long tournamentAdminId,
            @PathVariable Long bracketId,
            @PathVariable Long slotId) {
        return ResponseEntity.ok(bracketService.undoWinner(tournamentAdminId, bracketId, slotId));
    }
}
