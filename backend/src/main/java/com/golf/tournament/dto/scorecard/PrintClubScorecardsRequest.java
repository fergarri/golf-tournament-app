package com.golf.tournament.dto.scorecard;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Impresión de tarjetas de un club, sin torneo.
 * Si {@code playerIds} tiene valores, se imprimen esas tarjetas (tee de caballeros y de damas).
 * Si no, se imprimen {@code cantidad} tarjetas vacías del {@code teeId} indicado.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrintClubScorecardsRequest {

    @NotNull(message = "Debe seleccionar un club")
    private Long courseId;

    @NotNull(message = "Debe indicar la cantidad de hoyos")
    private Integer cantidadHoyos;

    /** Tee único, usado al imprimir tarjetas vacías. */
    private Long teeId;

    private Long teeMasculinoId;

    private Long teeFemeninoId;

    private List<Long> playerIds;

    /** Cantidad de tarjetas vacías. Se ignora cuando hay jugadores seleccionados. */
    private Integer cantidad;
}
