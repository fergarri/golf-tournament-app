package com.golf.tournament.controller;

import com.golf.tournament.dto.teesheet.PublicTeeSheetDTO;
import com.golf.tournament.service.TournamentTeeSheetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/public/tournaments/code/{codigo}/tee-sheet")
@RequiredArgsConstructor
public class PublicTournamentTeeSheetController {

    private final TournamentTeeSheetService teeSheetService;

    @GetMapping
    public ResponseEntity<PublicTeeSheetDTO> getPublicTeeSheet(@PathVariable String codigo) {
        return ResponseEntity.ok(teeSheetService.getPublic(codigo));
    }
}
