package com.golf.tournament.dto.tournamentadmin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PlayoffMatchPlayerActionRequest {
    @NotBlank(message = "La matrícula es obligatoria")
    private String matricula;
}
