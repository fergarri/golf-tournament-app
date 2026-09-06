package com.golf.tournament.dto.tournamentadmin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PlayoffMatchAccessRequest {
    @NotBlank(message = "La matrícula es obligatoria")
    private String matricula;
}
