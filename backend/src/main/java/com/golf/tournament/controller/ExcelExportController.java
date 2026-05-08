package com.golf.tournament.controller;

import com.golf.tournament.service.ExcelExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/exports/excel")
@RequiredArgsConstructor
public class ExcelExportController {

    private static final MediaType XLSX_MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ExcelExportService excelExportService;

    @GetMapping("/tournaments/{tournamentId}/inscriptions")
    @PreAuthorize("hasAnyAuthority('TOTAL', 'GAMES')")
    public ResponseEntity<byte[]> exportTournamentInscriptions(@PathVariable Long tournamentId) {
        byte[] content = excelExportService.exportTournamentInscriptions(tournamentId);
        String filename = "inscriptos_torneo_" + tournamentId + ".xlsx";
        return buildResponse(content, filename);
    }

    @GetMapping("/tournament-admin/{tournamentAdminId}/inscriptions")
    @PreAuthorize("hasAnyAuthority('TOTAL', 'ADMINISTRATION')")
    public ResponseEntity<byte[]> exportAdminInscriptions(@PathVariable Long tournamentAdminId) {
        byte[] content = excelExportService.exportAdminInscriptions(tournamentAdminId);
        String filename = "inscriptos_admin_" + tournamentAdminId + ".xlsx";
        return buildResponse(content, filename);
    }

    @GetMapping("/tournaments/{tournamentId}/results")
    @PreAuthorize("hasAnyAuthority('TOTAL', 'GAMES')")
    public ResponseEntity<byte[]> exportTournamentResults(@PathVariable Long tournamentId) {
        byte[] content = excelExportService.exportTournamentResults(tournamentId);
        String filename = "resultados_torneo_" + tournamentId + ".xlsx";
        return buildResponse(content, filename);
    }

    @GetMapping("/tournament-admin/{tournamentAdminId}/stages/{stageId}/board")
    @PreAuthorize("hasAnyAuthority('TOTAL', 'ADMINISTRATION')")
    public ResponseEntity<byte[]> exportStageBoard(
            @PathVariable Long tournamentAdminId,
            @PathVariable Long stageId) {
        byte[] content = excelExportService.exportStageBoard(tournamentAdminId, stageId);
        String filename = "etapa_" + stageId + ".xlsx";
        return buildResponse(content, filename);
    }

    @GetMapping("/tournament-admin/{tournamentAdminId}/playoff-results")
    @PreAuthorize("hasAnyAuthority('TOTAL', 'ADMINISTRATION')")
    public ResponseEntity<byte[]> exportPlayoffResults(@PathVariable Long tournamentAdminId) {
        byte[] content = excelExportService.exportPlayoffResults(tournamentAdminId);
        String filename = "playoff_" + tournamentAdminId + ".xlsx";
        return buildResponse(content, filename);
    }

    private ResponseEntity<byte[]> buildResponse(byte[] content, String filename) {
        return ResponseEntity.ok()
                .contentType(XLSX_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(content.length))
                .body(content);
    }
}
