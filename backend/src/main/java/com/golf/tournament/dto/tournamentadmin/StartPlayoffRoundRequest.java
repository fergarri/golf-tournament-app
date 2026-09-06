package com.golf.tournament.dto.tournamentadmin;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StartPlayoffRoundRequest {
    private Long teeMasculinoId;
    private Long teeFemeninoId;

    @NotNull(message = "La cantidad de hoyos a jugar es obligatoria")
    private Integer cantidadHoyosJuego;
}
