package com.golf.tournament.dto.player;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class CreatePlayerRequest {
    
    @NotBlank(message = "Name is required")
    private String nombre;
    
    @NotBlank(message = "Last name is required")
    private String apellido;
    
    private String email;
    
    @NotBlank(message = "Registration number is required")
    private String matricula;
    
    private LocalDate fechaNacimiento;
    
    @NotNull(message = "Handicap index is required")
    private BigDecimal handicapIndex;
    
    private String telefono;
    private String clubOrigen;
}
