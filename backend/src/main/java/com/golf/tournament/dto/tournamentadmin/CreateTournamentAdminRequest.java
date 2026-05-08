package com.golf.tournament.dto.tournamentadmin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTournamentAdminRequest {

    @NotBlank(message = "El nombre del torneo es requerido")
    private String nombre;

    @NotNull(message = "La fecha del torneo es requerida")
    private LocalDate fecha;

    @NotBlank(message = "El tipo del torneo es requerido")
    private String tipo;

    @NotNull(message = "El valor de inscripción es requerido")
    private BigDecimal valorInscripcion;

    @NotNull(message = "La cantidad de cuotas es requerida")
    @Min(value = 1, message = "La cantidad de cuotas debe ser al menos 1")
    private Integer cantidadCuotas;
}
