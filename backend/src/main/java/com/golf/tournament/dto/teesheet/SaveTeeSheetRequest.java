package com.golf.tournament.dto.teesheet;

import jakarta.validation.Valid;
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
public class SaveTeeSheetRequest {

    @NotNull(message = "La grilla es obligatoria")
    @Valid
    private List<GroupRequest> groups;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupRequest {
        private Integer holeNumber;
        /** HH:mm, o null. */
        private String teeTime;
        private Long player1Id;
        private Long player2Id;
        private Long player3Id;
        private Long player4Id;
    }
}
