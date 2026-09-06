package com.golf.tournament.dto.tournamentadmin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class UpdatePlayoffMatchHolesRequest {

    @NotBlank(message = "La matrícula es obligatoria")
    private String matricula;

    @NotEmpty(message = "Debe incluir al menos un hoyo")
    private List<HoleScoreUpdate> holeScores;

    @Data
    public static class HoleScoreUpdate {
        @NotNull(message = "holeSequence es obligatorio")
        private Integer holeSequence;
        private Integer golpesPropio;
        private Integer golpesRival;
    }
}
