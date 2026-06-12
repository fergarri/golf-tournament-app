package com.golf.tournament.dto.tournamentadmin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoringConfigDTO {

    private Long id;
    private Long tournamentAdminId;
    private Integer birdiePoints;
    private Integer eaglePoints;
    private Integer acePoints;
    private Integer participationPoints;
    private Integer remainingPositionsPoints;
    private Integer qualifiedPlayoffPositions;
    /** Clasificados Sin HCP (Scratch). 0 = sin clasificación. Solo CLASICO. */
    private Integer qualifiedPlayoffPositionsScratch;
    /** GLOBAL o PER_CATEGORY. Solo relevante para torneos CLASICO. */
    private String hcpQualifiedMode;
    private String tieBreakMode;
    private List<PositionPointsDTO> positionPoints;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PositionPointsDTO {
        private Integer position;
        private Integer points;
    }
}
