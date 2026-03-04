package com.golf.tournament.controller;

import com.golf.tournament.dto.tournamentadmin.TournamentAdminStageBoardDTO;
import com.golf.tournament.service.TournamentAdminStageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/public/tournament-admin/{tournamentAdminId}/stages")
@RequiredArgsConstructor
public class PublicTournamentAdminStageController {

    private final TournamentAdminStageService stageService;

    @GetMapping("/{stageId}/board")
    public ResponseEntity<TournamentAdminStageBoardDTO> getPublicStageBoard(
            @PathVariable Long tournamentAdminId,
            @PathVariable Long stageId) {
        return ResponseEntity.ok(stageService.getStageBoard(tournamentAdminId, stageId));
    }
}
