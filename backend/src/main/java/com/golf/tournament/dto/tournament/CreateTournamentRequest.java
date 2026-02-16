package com.golf.tournament.dto.tournament;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTournamentRequest {
    
    @NotBlank(message = "Tournament name is required")
    private String nombre;
    
    @NotBlank(message = "Tournament type is required")
    private String tipo;
    
    @NotBlank(message = "Scoring modality is required")
    private String modalidad;
    
    @NotNull(message = "Course ID is required")
    private Long courseId;
    
    @NotNull(message = "Start date is required")
    private LocalDate fechaInicio;
    
    private LocalDate fechaFin;
    private Integer limiteInscriptos;
    private BigDecimal valorInscripcion;
    
    @NotNull(message = "At least one category is required")
    private List<TournamentCategoryDTO> categories;
}
