package com.golf.tournament.dto.tournamentadmin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportAdminInscriptionsResultDTO {
    private Integer relatedPendingTournaments;
    private Integer importedCount;
    private Integer skippedAlreadyInscribed;
    private Integer skippedByCapacity;
}
