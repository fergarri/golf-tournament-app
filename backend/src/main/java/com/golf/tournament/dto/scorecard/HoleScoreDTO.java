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
    /**
     * Estado de concordancia para la fila "MARCAR A..." del que marca.
     * MATCH: ambos cargaron el mismo valor (verde).
     * MISMATCH: ambos cargaron pero difieren (rojo).
     * PENDING: falta el dato de alguno de los dos (amarillo).
     * NONE: no aplica (sin marcador asignado o scorecard sin cruce).
     */
    private String estadoConcordancia;
}
