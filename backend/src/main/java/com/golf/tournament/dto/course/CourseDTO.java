package com.golf.tournament.dto.course;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseDTO {
    
    private Long id;
    private String nombre;
    private String pais;
    private String provincia;
    private String ciudad;
    private Integer cantidadHoyos;
    private BigDecimal courseRating;
    private Integer slopeRating;
    private List<CourseTeeDTO> tees;
    private List<HoleDTO> holes;
}
