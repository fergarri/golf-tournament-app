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
public class LeaderboardEntryDTO {
    
    private Integer position;
    private Long scorecardId;
    private Long playerId;
    private String playerName;
    private String matricula;
    private String clubOrigen;
    private String categoryName;
    private Integer scoreGross;
    private BigDecimal scoreNeto;
    private Integer totalPar;
    private BigDecimal scoreToPar;
    private BigDecimal handicapCourse;
}
