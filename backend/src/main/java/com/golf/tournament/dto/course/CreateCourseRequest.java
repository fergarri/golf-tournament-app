package com.golf.tournament.dto.course;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateCourseRequest {
    
    @NotBlank(message = "Course name is required")
    private String nombre;
    
    @NotBlank(message = "Country is required")
    private String pais;
    
    private String provincia;
    private String ciudad;
    
    @NotNull(message = "Number of holes is required")
    private Integer cantidadHoyos;
    
    private BigDecimal courseRating;
    private Integer slopeRating;
}
