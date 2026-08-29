package com.golf.tournament.dto.course;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportHandicapConversionTeeResultDTO {
    private Long teeId;
    private String teeNombre;
    private String genero;
    private int matchedRows;
    private boolean imported;
    private String message;
}
