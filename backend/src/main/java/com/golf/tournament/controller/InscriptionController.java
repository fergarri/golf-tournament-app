package com.golf.tournament.controller;

import com.golf.tournament.dto.inscription.InscriptionRequest;
import com.golf.tournament.dto.inscription.InscriptionResponse;
import com.golf.tournament.service.InscriptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/inscriptions")
@RequiredArgsConstructor
public class InscriptionController {

    private final InscriptionService inscriptionService;

    @PostMapping("/tournaments/{codigo}")
    public ResponseEntity<InscriptionResponse> inscribePlayer(
            @PathVariable String codigo,
            @Valid @RequestBody InscriptionRequest request) {
        InscriptionResponse response = inscriptionService.inscribePlayer(codigo, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/admin/tournaments/{tournamentId}/players/{playerId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<InscriptionResponse> inscribePlayerManual(
            @PathVariable Long tournamentId,
            @PathVariable Long playerId) {
        InscriptionResponse response = inscriptionService.inscribePlayerManual(tournamentId, playerId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/tournaments/{tournamentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<InscriptionResponse>> getTournamentInscriptions(@PathVariable Long tournamentId) {
        return ResponseEntity.ok(inscriptionService.getTournamentInscriptions(tournamentId));
    }

    @PatchMapping("/{inscriptionId}/handicap-course")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> updateHandicapCourse(
            @PathVariable Long inscriptionId,
            @RequestParam BigDecimal handicapCourse) {
        inscriptionService.updateHandicapCourse(inscriptionId, handicapCourse);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{inscriptionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> removeInscription(@PathVariable Long inscriptionId) {
        inscriptionService.removeInscription(inscriptionId);
        return ResponseEntity.noContent().build();
    }
}
