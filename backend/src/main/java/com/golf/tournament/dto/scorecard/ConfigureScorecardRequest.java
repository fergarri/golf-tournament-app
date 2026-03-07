package com.golf.tournament.dto.scorecard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigureScorecardRequest {
    private Long teeId;
    private Integer cantidadHoyosJuego;
    private String inProgressAction;
}
