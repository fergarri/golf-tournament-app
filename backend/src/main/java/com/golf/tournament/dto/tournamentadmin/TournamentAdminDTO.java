package com.golf.tournament.dto.tournamentadmin;

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
public class TournamentAdminDTO {

    private Long id;
    private String nombre;
    private LocalDate fecha;
    private Long tournamentId;
    private String tournamentNombre;
    private BigDecimal valorInscripcion;
    private Integer cantidadCuotas;
    private String estado;
    private Integer currentInscriptos;
    private BigDecimal totalRecaudado;
}
