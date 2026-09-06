package com.golf.tournament.controller;

import com.golf.tournament.dto.tournamentadmin.PlayoffRoundSessionDTO;
import com.golf.tournament.service.TournamentAdminPlayoffMatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Resumen en vivo de las rondas/partidos de Match Play de una llave, para el chip
 * de estado que se muestra en la vista pública de la llave. Ver docs/diseno-match-play-playoff.md.
 */
@RestController
@RequestMapping("/public/tournament-admin/{tournamentAdminId}/playoff-brackets/{bracketId}/rounds")
@RequiredArgsConstructor
public class PublicTournamentAdminPlayoffMatchController {

    private final TournamentAdminPlayoffMatchService matchService;

    @GetMapping
    public ResponseEntity<List<PlayoffRoundSessionDTO>> getRoundSessions(
            @PathVariable Long tournamentAdminId,
            @PathVariable Long bracketId) {
        return ResponseEntity.ok(matchService.getPublicRoundSessions(tournamentAdminId, bracketId));
    }
}
