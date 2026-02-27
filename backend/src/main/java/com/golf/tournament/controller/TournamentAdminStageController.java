package com.golf.tournament.controller;

import com.golf.tournament.dto.tournamentadmin.*;
import com.golf.tournament.service.TournamentAdminStageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tournament-admin/{tournamentAdminId}/stages")
@PreAuthorize("hasAnyAuthority('TOTAL', 'ADMINISTRATION')")
@RequiredArgsConstructor
public class TournamentAdminStageController {

    private final TournamentAdminStageService stageService;

    @GetMapping
    public ResponseEntity<List<TournamentAdminStageDTO>> getStages(@PathVariable Long tournamentAdminId) {
        return ResponseEntity.ok(stageService.getStages(tournamentAdminId));
    }

    @GetMapping("/{stageId}")
    public ResponseEntity<TournamentAdminStageDTO> getStage(
            @PathVariable Long tournamentAdminId,
            @PathVariable Long stageId) {
        return ResponseEntity.ok(stageService.getStage(tournamentAdminId, stageId));
    }

    @GetMapping("/relations/options")
    public ResponseEntity<List<TournamentRelationOptionDTO>> getRelationOptions(
            @PathVariable Long tournamentAdminId,
            @RequestParam(required = false) Long stageId) {
        return ResponseEntity.ok(stageService.getStageRelationOptions(tournamentAdminId, stageId));
    }

    @PostMapping
    public ResponseEntity<TournamentAdminStageDTO> createStage(
            @PathVariable Long tournamentAdminId,
            @Valid @RequestBody CreateTournamentAdminStageRequest request) {
        return ResponseEntity.ok(stageService.createStage(tournamentAdminId, request));
    }

    @PutMapping("/{stageId}")
    public ResponseEntity<TournamentAdminStageDTO> updateStage(
            @PathVariable Long tournamentAdminId,
            @PathVariable Long stageId,
            @Valid @RequestBody UpdateTournamentAdminStageRequest request) {
        return ResponseEntity.ok(stageService.updateStage(tournamentAdminId, stageId, request));
    }

    @GetMapping("/{stageId}/board")
    public ResponseEntity<TournamentAdminStageBoardDTO> getStageBoard(
            @PathVariable Long tournamentAdminId,
            @PathVariable Long stageId) {
        return ResponseEntity.ok(stageService.getStageBoard(tournamentAdminId, stageId));
    }

    @PostMapping("/{stageId}/calculate")
    public ResponseEntity<TournamentAdminStageBoardDTO> calculateStage(
            @PathVariable Long tournamentAdminId,
            @PathVariable Long stageId) {
        return ResponseEntity.ok(stageService.calculateStageScores(tournamentAdminId, stageId));
    }
}
