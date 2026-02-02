package com.golf.tournament.controller;

import com.golf.tournament.dto.leaderboard.LeaderboardEntryDTO;
import com.golf.tournament.service.LeaderboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/leaderboard")
@RequiredArgsConstructor
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    @GetMapping("/tournaments/{tournamentId}")
    public ResponseEntity<List<LeaderboardEntryDTO>> getLeaderboard(@PathVariable Long tournamentId) {
        return ResponseEntity.ok(leaderboardService.getLeaderboardByCategory(tournamentId));
    }

    @GetMapping("/tournaments/{tournamentId}/categories/{categoryId}")
    public ResponseEntity<List<LeaderboardEntryDTO>> getLeaderboardByCategory(
            @PathVariable Long tournamentId,
            @PathVariable Long categoryId) {
        return ResponseEntity.ok(leaderboardService.getLeaderboard(tournamentId, categoryId));
    }
}
