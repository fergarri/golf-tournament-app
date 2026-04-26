package com.golf.tournament.dto.player;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkUpdateHandicapChangeItem {
    private String matricula;
    private String nombre;
    private String apellido;
    private BigDecimal handicapAnterior;
    private BigDecimal handicapNuevo;
}
