package com.golf.tournament.dto.tournament;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentCategoryDTO {
    
    private Long id;
    private String nombre;
    private BigDecimal handicapMin;
    private BigDecimal handicapMax;
}
