package com.golf.tournament.dto.scorecard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoleScoreDTO {
    
    private Long id;
    private Long holeId;
    private Integer numeroHoyo;
    private Integer par;
    private Integer golpesPropio;
    private Integer golpesMarcador;
    private Boolean validado;
}
