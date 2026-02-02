package com.golf.tournament.dto.scorecard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateScorecardRequest {
    
    private List<HoleScoreUpdate> holeScores;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HoleScoreUpdate {
        private Long holeId;
        private Integer golpesPropio;
        private Integer golpesMarcador;
    }
}
