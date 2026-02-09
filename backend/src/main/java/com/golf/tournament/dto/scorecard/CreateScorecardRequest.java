package com.golf.tournament.dto.scorecard;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateScorecardRequest {
    
    @NotNull(message = "Tee ID is required")
    private Long teeId;
}
