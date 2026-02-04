package com.golf.tournament.dto.scorecard;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateScorecardRequest {
    
    @NotNull(message = "Handicap course is required")
    private BigDecimal handicapCourse;
}
