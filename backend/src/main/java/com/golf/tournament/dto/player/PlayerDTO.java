package com.golf.tournament.dto.player;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerDTO {
    
    private Long id;
    private String nombre;
    private String apellido;
    private String email;
    private String matricula;
    private LocalDate fechaNacimiento;
    private BigDecimal handicapIndex;
    private String telefono;
    private String clubOrigen;
}
