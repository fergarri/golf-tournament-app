package com.golf.tournament.controller;

import com.golf.tournament.dto.tournamentadmin.*;
import com.golf.tournament.service.TournamentAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tournament-admin")
@PreAuthorize("hasAnyAuthority('TOTAL', 'ADMINISTRATION')")
@RequiredArgsConstructor
public class TournamentAdminController {

    private final TournamentAdminService tournamentAdminService;

    @GetMapping
    public ResponseEntity<List<TournamentAdminDTO>> getAll() {
        return ResponseEntity.ok(tournamentAdminService.getAll());
    }

    @GetMapping("/relations/options")
    public ResponseEntity<List<TournamentRelationOptionDTO>> getRelationOptions(
            @RequestParam(required = false) Long adminId) {
        return ResponseEntity.ok(tournamentAdminService.getRelationOptions(adminId));
    }

    @PostMapping
    public ResponseEntity<TournamentAdminDTO> create(@Valid @RequestBody CreateTournamentAdminRequest request) {
        return ResponseEntity.ok(tournamentAdminService.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TournamentAdminDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(tournamentAdminService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TournamentAdminDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTournamentAdminRequest request) {
        return ResponseEntity.ok(tournamentAdminService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        tournamentAdminService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/finalize")
    public ResponseEntity<TournamentAdminDTO> finalize(@PathVariable Long id) {
        return ResponseEntity.ok(tournamentAdminService.finalize(id));
    }

    @PostMapping("/{id}/inscriptions/{playerId}")
    public ResponseEntity<Void> inscribePlayer(
            @PathVariable Long id,
            @PathVariable Long playerId) {
        tournamentAdminService.inscribePlayer(id, playerId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/inscriptions/{inscriptionId}")
    public ResponseEntity<Void> removeInscription(@PathVariable Long inscriptionId) {
        tournamentAdminService.removeInscription(inscriptionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/detail")
    public ResponseEntity<TournamentAdminDetailDTO> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(tournamentAdminService.getDetail(id));
    }

    @PutMapping("/{id}/payments")
    public ResponseEntity<Void> savePayments(
            @PathVariable Long id,
            @RequestBody SavePaymentsRequest request) {
        tournamentAdminService.savePayments(id, request);
        return ResponseEntity.ok().build();
    }
}
