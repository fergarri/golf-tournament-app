package com.golf.tournament.controller;

import com.golf.tournament.dto.tournamentadmin.TournamentAdminPlayoffResultsDTO;
import com.golf.tournament.service.TournamentAdminPlayoffResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tournament-admin/{tournamentAdminId}/stages/playoff-results")
@PreAuthorize("hasAnyAuthority('TOTAL', 'ADMINISTRATION')")
@RequiredArgsConstructor
public class TournamentAdminPlayoffResultController {

    private final TournamentAdminPlayoffResultService playoffResultService;

    @GetMapping
    public ResponseEntity<TournamentAdminPlayoffResultsDTO> getResults(@PathVariable Long tournamentAdminId) {
        return ResponseEntity.ok(playoffResultService.getResults(tournamentAdminId));
    }

    @PostMapping("/calculate")
    public ResponseEntity<TournamentAdminPlayoffResultsDTO> calculateResults(@PathVariable Long tournamentAdminId) {
        return ResponseEntity.ok(playoffResultService.calculateResults(tournamentAdminId));
    }
}
