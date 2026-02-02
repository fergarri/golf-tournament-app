package com.golf.tournament.controller;

import com.golf.tournament.dto.tournament.CreateTournamentRequest;
import com.golf.tournament.dto.tournament.TournamentDTO;
import com.golf.tournament.service.TournamentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tournaments")
@RequiredArgsConstructor
public class TournamentController {

    private final TournamentService tournamentService;

    @GetMapping
    public ResponseEntity<List<TournamentDTO>> getAllTournaments() {
        return ResponseEntity.ok(tournamentService.getAllTournaments());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TournamentDTO> getTournamentById(@PathVariable Long id) {
        return ResponseEntity.ok(tournamentService.getTournamentById(id));
    }

    @GetMapping("/code/{codigo}")
    public ResponseEntity<TournamentDTO> getTournamentByCodigo(@PathVariable String codigo) {
        return ResponseEntity.ok(tournamentService.getTournamentByCodigo(codigo));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TournamentDTO> createTournament(@Valid @RequestBody CreateTournamentRequest request) {
        TournamentDTO tournament = tournamentService.createTournament(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(tournament);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TournamentDTO> updateTournament(
            @PathVariable Long id,
            @Valid @RequestBody CreateTournamentRequest request) {
        return ResponseEntity.ok(tournamentService.updateTournament(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteTournament(@PathVariable Long id) {
        tournamentService.deleteTournament(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TournamentDTO> startTournament(@PathVariable Long id) {
        return ResponseEntity.ok(tournamentService.startTournament(id));
    }

    @PostMapping("/{id}/finalize")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TournamentDTO> finalizeTournament(@PathVariable Long id) {
        return ResponseEntity.ok(tournamentService.finalizeTournament(id));
    }
}
