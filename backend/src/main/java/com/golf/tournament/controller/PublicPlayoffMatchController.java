package com.golf.tournament.controller;

import com.golf.tournament.dto.tournamentadmin.PlayoffMatchAccessRequest;
import com.golf.tournament.dto.tournamentadmin.PlayoffMatchAccessResponseDTO;
import com.golf.tournament.dto.tournamentadmin.PlayoffMatchPlayerActionRequest;
import com.golf.tournament.dto.tournamentadmin.PlayoffMatchStateDTO;
import com.golf.tournament.dto.tournamentadmin.UpdatePlayoffMatchHolesRequest;
import com.golf.tournament.service.TournamentAdminPlayoffMatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Acceso y carga de la tarjeta virtual de un partido de Match Play, vía el código de la
 * ronda (sin autenticación). Ver docs/diseno-match-play-playoff.md.
 */
@RestController
@RequestMapping("/public/playoff-match-rounds/{code}")
@RequiredArgsConstructor
public class PublicPlayoffMatchController {

    private final TournamentAdminPlayoffMatchService matchService;

    @PostMapping("/access")
    public ResponseEntity<PlayoffMatchAccessResponseDTO> access(
            @PathVariable String code,
            @Valid @RequestBody PlayoffMatchAccessRequest request) {
        return ResponseEntity.ok(matchService.accessByCode(code, request.getMatricula()));
    }

    @GetMapping("/matches/{matchId}")
    public ResponseEntity<PlayoffMatchStateDTO> getMatchState(
            @PathVariable String code,
            @PathVariable Long matchId,
            @RequestParam String matricula) {
        return ResponseEntity.ok(matchService.getMatchState(code, matchId, matricula));
    }

    @PutMapping("/matches/{matchId}/holes")
    public ResponseEntity<PlayoffMatchStateDTO> updateHoles(
            @PathVariable String code,
            @PathVariable Long matchId,
            @Valid @RequestBody UpdatePlayoffMatchHolesRequest request) {
        return ResponseEntity.ok(matchService.updateHoles(code, matchId, request));
    }

    @PostMapping("/matches/{matchId}/deliver")
    public ResponseEntity<PlayoffMatchStateDTO> deliver(
            @PathVariable String code,
            @PathVariable Long matchId,
            @Valid @RequestBody PlayoffMatchPlayerActionRequest request) {
        return ResponseEntity.ok(matchService.deliverMatch(code, matchId, request.getMatricula()));
    }

    @PostMapping("/matches/{matchId}/concede")
    public ResponseEntity<PlayoffMatchStateDTO> concede(
            @PathVariable String code,
            @PathVariable Long matchId,
            @Valid @RequestBody PlayoffMatchPlayerActionRequest request) {
        return ResponseEntity.ok(matchService.concedeMatch(code, matchId, request.getMatricula()));
    }
}
