package com.golf.tournament.controller;

import com.golf.tournament.dto.teesheet.SaveTeeSheetRequest;
import com.golf.tournament.dto.teesheet.TournamentTeeSheetDTO;
import com.golf.tournament.service.TournamentTeeSheetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tournaments/{tournamentId}/tee-sheet")
@PreAuthorize("hasAnyAuthority('TOTAL', 'GAMES')")
@RequiredArgsConstructor
public class TournamentTeeSheetController {

    private final TournamentTeeSheetService teeSheetService;

    @GetMapping
    public ResponseEntity<TournamentTeeSheetDTO> getTeeSheet(@PathVariable Long tournamentId) {
        return ResponseEntity.ok(teeSheetService.getOrCreate(tournamentId));
    }

    @PutMapping
    public ResponseEntity<TournamentTeeSheetDTO> saveTeeSheet(
            @PathVariable Long tournamentId,
            @Valid @RequestBody SaveTeeSheetRequest request) {
        return ResponseEntity.ok(teeSheetService.save(tournamentId, request));
    }

    @PostMapping("/confirm")
    public ResponseEntity<TournamentTeeSheetDTO> confirm(@PathVariable Long tournamentId) {
        return ResponseEntity.ok(teeSheetService.confirm(tournamentId));
    }

    @PostMapping("/edit")
    public ResponseEntity<TournamentTeeSheetDTO> edit(@PathVariable Long tournamentId) {
        return ResponseEntity.ok(teeSheetService.unlock(tournamentId));
    }
}
