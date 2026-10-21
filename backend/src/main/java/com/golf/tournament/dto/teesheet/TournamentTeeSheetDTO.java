package com.golf.tournament.dto.teesheet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentTeeSheetDTO {

    private Long tournamentId;
    private String tournamentName;
    private String codigo;
    private String courseName;
    private LocalDate fechaInicio;
    /** 9 o 18, según la cantidad de hoyos del torneo. */
    private Integer cantidadHoyosJuego;
    /** true cuando la grilla de trabajo está bloqueada. */
    private Boolean locked;
    /** true si ya existe una grilla confirmada publicada. */
    private Boolean published;
    private List<GroupDTO> groups;
    private List<PlayerDTO> players;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupDTO {
        private Integer holeNumber;
        /** HH:mm, o null si no hay horario. */
        private String teeTime;
        private Long player1Id;
        private Long player2Id;
        private Long player3Id;
        private Long player4Id;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlayerDTO {
        private Long playerId;
        private String playerName;
        private String nombre;
        private String apellido;
        private BigDecimal handicapIndex;
    }
}
