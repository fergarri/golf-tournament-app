package com.golf.tournament.dto.teesheet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicTeeSheetDTO {

    private String tournamentName;
    private String courseName;
    private LocalDate fechaInicio;
    /** false si todavía no se confirmó ninguna grilla. */
    private Boolean published;
    private List<GroupDTO> groups;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupDTO {
        private Integer holeNumber;
        /** HH:mm, o null si no hay horario. */
        private String teeTime;
        private String player1Name;
        private String player2Name;
        private String player3Name;
        private String player4Name;
    }
}
