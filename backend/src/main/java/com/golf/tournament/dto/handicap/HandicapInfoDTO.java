package com.golf.tournament.dto.handicap;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HandicapInfoDTO {
    
    private String nombreCompleto;
    private String matricula;
    private BigDecimal handicapIndex;
    private String club;
}
