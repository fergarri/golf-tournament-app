package com.golf.tournament.dto.inscription;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InscriptionRequest {
    
    @NotBlank(message = "Registration number is required")
    private String matricula;
}
