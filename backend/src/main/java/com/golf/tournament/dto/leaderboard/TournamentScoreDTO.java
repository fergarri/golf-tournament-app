package com.golf.tournament.dto.leaderboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentScoreDTO {

    private Long scorecardId;
    private Long playerId;
    private String playerName;
    private String matricula;
    private Integer position;
    private BigDecimal handicapIndex;
    private BigDecimal handicapCourse;
    private Integer scoreGross;
    private BigDecimal scoreNeto;
    private String status;
    private Integer birdieCount;
    private Integer eagleCount;
    private Integer aceCount;
    private Integer positionPoints;
    private Integer birdiePoints;
    private Integer eaglePoints;
    private Integer acePoints;
    private Integer participationPoints;
    private Integer totalPoints;

    /** GLOBAL, CATEGORY o SCRATCH */
    private String scoreType;

    /** Solo cuando scoreType = CATEGORY: id de la categoría. */
    private Long categoryId;

    /** Nombre de categoría (solo para CATEGORY). */
    private String categoryName;
}
