package com.golf.tournament.dto.tournamentadmin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTournamentAdminStageRequest {

    @NotBlank(message = "El nombre de la etapa es requerido")
    private String nombre;

    @NotEmpty(message = "Debe seleccionar al menos una fecha")
    private List<Long> tournamentIds;
}
