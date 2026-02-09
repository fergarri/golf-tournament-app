package com.golf.tournament.controller;

import com.golf.tournament.dto.player.BulkUpdateResponse;
import com.golf.tournament.dto.player.CreatePlayerRequest;
import com.golf.tournament.dto.player.PlayerDTO;
import com.golf.tournament.exception.BadRequestException;
import com.golf.tournament.service.PlayerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/players")
@RequiredArgsConstructor
public class PlayerController {

    private final PlayerService playerService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PlayerDTO>> getAllPlayers() {
        return ResponseEntity.ok(playerService.getAllPlayers());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PlayerDTO> getPlayerById(@PathVariable Long id) {
        return ResponseEntity.ok(playerService.getPlayerById(id));
    }

    @GetMapping("/matricula/{matricula}")
    public ResponseEntity<PlayerDTO> getPlayerByMatricula(@PathVariable String matricula) {
        return ResponseEntity.ok(playerService.getPlayerByMatricula(matricula));
    }

    @GetMapping("/search")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<PlayerDTO>> searchPlayers(@RequestParam String query) {
        return ResponseEntity.ok(playerService.searchPlayers(query));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PlayerDTO> createPlayer(@Valid @RequestBody CreatePlayerRequest request) {
        PlayerDTO player = playerService.createPlayer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(player);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PlayerDTO> updatePlayer(
            @PathVariable Long id,
            @Valid @RequestBody CreatePlayerRequest request) {
        return ResponseEntity.ok(playerService.updatePlayer(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deletePlayer(@PathVariable Long id) {
        playerService.deletePlayer(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bulk-update")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BulkUpdateResponse> bulkUpdatePlayers(
            @RequestParam("file") MultipartFile file) {
        
        if (file.isEmpty()) {
            throw new BadRequestException("El archivo está vacío");
        }
        
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.endsWith(".xlsx")) {
            throw new BadRequestException("El archivo debe ser formato .xlsx");
        }
        
        BulkUpdateResponse response = playerService.bulkUpdatePlayers(file);
        return ResponseEntity.ok(response);
    }
}
