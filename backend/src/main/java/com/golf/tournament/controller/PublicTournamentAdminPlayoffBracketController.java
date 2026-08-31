package com.golf.tournament.controller;

import com.golf.tournament.dto.tournamentadmin.TournamentAdminPlayoffBracketsDTO;
import com.golf.tournament.service.TournamentAdminPlayoffBracketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/public/tournament-admin/{tournamentAdminId}/playoff-brackets")
@RequiredArgsConstructor
public class PublicTournamentAdminPlayoffBracketController {

    private final TournamentAdminPlayoffBracketService bracketService;

    @GetMapping
    public ResponseEntity<TournamentAdminPlayoffBracketsDTO> getPublicBrackets(
            @PathVariable Long tournamentAdminId) {
        return ResponseEntity.ok(bracketService.getPublicBrackets(tournamentAdminId));
    }
}
