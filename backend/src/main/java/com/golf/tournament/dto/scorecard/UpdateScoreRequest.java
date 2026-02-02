package com.golf.tournament.dto.scorecard;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateScoreRequest {
    
    @NotNull(message = "Hole ID is required")
    private Long holeId;
    
    @NotNull(message = "Score is required")
    @Min(value = 1, message = "Score must be at least 1")
    private Integer golpes;
    
    @NotNull(message = "Score type is required")
    private String tipo;
}
