package com.golf.tournament.controller;

import com.golf.tournament.dto.scorecard.CreateScorecardRequest;
import com.golf.tournament.dto.scorecard.ScorecardDTO;
import com.golf.tournament.dto.scorecard.UpdateScoreRequest;
import com.golf.tournament.dto.scorecard.UpdateScorecardRequest;
import com.golf.tournament.service.ScorecardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/scorecards")
@RequiredArgsConstructor
public class ScorecardController {

    private final ScorecardService scorecardService;

    @GetMapping("/{id}")
    public ResponseEntity<ScorecardDTO> getScorecardById(@PathVariable Long id) {
        return ResponseEntity.ok(scorecardService.getScorecardById(id));
    }

    @GetMapping("/tournaments/{tournamentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ScorecardDTO>> getTournamentScorecards(@PathVariable Long tournamentId) {
        return ResponseEntity.ok(scorecardService.getTournamentScorecards(tournamentId));
    }

    @PostMapping("/tournaments/{tournamentId}/players/{playerId}")
    public ResponseEntity<ScorecardDTO> getOrCreateScorecard(
            @PathVariable Long tournamentId,
            @PathVariable Long playerId,
            @Valid @RequestBody CreateScorecardRequest request) {
        return ResponseEntity.ok(scorecardService.getOrCreateScorecard(tournamentId, playerId, request.getTeeId()));
    }

    @PatchMapping("/{scorecardId}/marker/{markerId}")
    public ResponseEntity<ScorecardDTO> assignMarker(
            @PathVariable Long scorecardId,
            @PathVariable Long markerId) {
        return ResponseEntity.ok(scorecardService.assignMarker(scorecardId, markerId));
    }

    @PatchMapping("/{scorecardId}/scores")
    public ResponseEntity<Void> updateScore(
            @PathVariable Long scorecardId,
            @Valid @RequestBody UpdateScoreRequest request) {
        scorecardService.updateScore(scorecardId, request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{scorecardId}")
    public ResponseEntity<ScorecardDTO> updateScorecard(
            @PathVariable Long scorecardId,
            @Valid @RequestBody UpdateScorecardRequest request) {
        return ResponseEntity.ok(scorecardService.updateScorecard(scorecardId, request));
    }

    @PostMapping("/{scorecardId}/deliver")
    public ResponseEntity<ScorecardDTO> deliverScorecard(@PathVariable Long scorecardId) {
        return ResponseEntity.ok(scorecardService.deliverScorecard(scorecardId));
    }

    @PostMapping("/{scorecardId}/cancel")
    public ResponseEntity<ScorecardDTO> cancelScorecard(@PathVariable Long scorecardId) {
        return ResponseEntity.ok(scorecardService.cancelScorecard(scorecardId));
    }
}
