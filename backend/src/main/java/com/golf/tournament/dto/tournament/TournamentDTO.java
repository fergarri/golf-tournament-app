package com.golf.tournament.dto.tournament;

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
public class TournamentDTO {
    
    private Long id;
    private String nombre;
    private String codigo;
    private String tipo;
    private String modalidad;
    private String estado;
    private Long courseId;
    private String courseName;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
    private Integer limiteInscriptos;
    private BigDecimal valorInscripcion;
    private Boolean doublePoints;
    private Integer currentInscriptos;
    private List<TournamentCategoryDTO> categories;
}
