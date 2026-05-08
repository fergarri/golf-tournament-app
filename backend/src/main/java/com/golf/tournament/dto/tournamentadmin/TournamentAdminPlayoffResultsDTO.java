package com.golf.tournament.dto.tournamentadmin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentAdminPlayoffResultsDTO {

    private Long tournamentAdminId;
    /** FRUTALES o CLASICO */
    private String tipo;
    private List<StageColumnDTO> stages;
    /** Filas Con HCP. Para FRUTALES es el único listado. */
    private List<RowDTO> rows;
    /** Filas Sin HCP (scratch). Solo poblado para tipo CLASICO. */
    private List<RowDTO> scratchRows;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StageColumnDTO {
        private Long stageId;
        private String code;
        private String stageName;
        private LocalDateTime stageCreatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RowDTO {
        private Long playerId;
        private String playerName;
        private Map<Long, Integer> pointsByStage;
        private Integer totalPoints;
        private Integer position;
        private Boolean qualified;
    }
}
