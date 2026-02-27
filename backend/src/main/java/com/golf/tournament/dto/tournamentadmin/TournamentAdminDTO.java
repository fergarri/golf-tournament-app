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
public class TournamentAdminDTO {

    private Long id;
    private String nombre;
    private LocalDate fecha;
    private String tournamentNombre;
    private List<Long> relatedTournamentIds;
    private List<RelatedTournamentDTO> relatedTournaments;
    private BigDecimal valorInscripcion;
    private Integer cantidadCuotas;
    private String estado;
    private Integer currentInscriptos;
    private BigDecimal totalRecaudado;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelatedTournamentDTO {
        private Long id;
        private String nombre;
    }
}
