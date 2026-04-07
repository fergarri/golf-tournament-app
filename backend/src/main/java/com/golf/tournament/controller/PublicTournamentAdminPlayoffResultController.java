package com.golf.tournament.controller;

import com.golf.tournament.dto.tournamentadmin.TournamentAdminPlayoffResultsDTO;
import com.golf.tournament.service.TournamentAdminPlayoffResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/public/tournament-admin/{tournamentAdminId}/playoff-results")
@RequiredArgsConstructor
public class PublicTournamentAdminPlayoffResultController {

    private final TournamentAdminPlayoffResultService playoffResultService;

    @GetMapping
    public ResponseEntity<TournamentAdminPlayoffResultsDTO> getPublicPlayoffResults(
            @PathVariable Long tournamentAdminId) {
        return ResponseEntity.ok(playoffResultService.getResults(tournamentAdminId));
    }
}
