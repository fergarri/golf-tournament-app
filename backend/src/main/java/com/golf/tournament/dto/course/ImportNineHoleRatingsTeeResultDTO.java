package com.golf.tournament.dto.course;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportNineHoleRatingsTeeResultDTO {
    private Long teeId;
    private String teeNombre;
    private String genero;
    private boolean imported;
    private boolean created;
    private BigDecimal courseRatingIda;
    private BigDecimal courseRatingVuelta;
    private Integer slopeRatingIda;
    private Integer slopeRatingVuelta;
    private String message;
}
