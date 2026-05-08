package com.golf.tournament.dto.tournamentadmin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportTournamentInscriptionsResultDTO {
    private Long tournamentAdminId;
    private String tournamentAdminNombre;
    private Integer importedCount;
    private Integer skippedAlreadyInscribed;
}
