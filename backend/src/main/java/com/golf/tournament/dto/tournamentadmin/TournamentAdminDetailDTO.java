package com.golf.tournament.dto.tournamentadmin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentAdminDetailDTO {

    private Long id;
    private String nombre;
    private LocalDate fecha;
    private Integer cantidadCuotas;
    private BigDecimal valorInscripcion;
    private Integer currentInscriptos;
    private BigDecimal totalRecaudado;
    private Boolean canManageStages;
    private List<TournamentAdminInscriptionDTO> inscriptions;
}
