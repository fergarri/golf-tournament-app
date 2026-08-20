package com.golf.tournament.dto.scorecard;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrintScorecardsRequest {

    @NotEmpty(message = "Debe seleccionar al menos un jugador para imprimir")
    private List<Long> playerIds;
}
