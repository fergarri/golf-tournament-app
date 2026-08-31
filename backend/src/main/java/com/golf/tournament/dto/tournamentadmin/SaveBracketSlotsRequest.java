package com.golf.tournament.dto.tournamentadmin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveBracketSlotsRequest {

    @NotEmpty
    @Valid
    private List<SlotAssignmentRequest> assignments;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SlotAssignmentRequest {
        @NotNull
        private Long slotId;

        /** null = dejar el casillero vacío. */
        private Long playerId;
    }
}
