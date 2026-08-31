package com.golf.tournament.dto.tournamentadmin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentAdminPlayoffBracketsDTO {

    private Long tournamentAdminId;
    /** FRUTALES o CLASICO */
    private String tipo;
    /** true si el torneo tiene clasificación Scratch configurada (solo CLASICO). */
    private Boolean scratchApplicable;
    /** Llaves ya generadas para este torneo (0, 1 o 2: HCP y/o SCRATCH). */
    private List<BracketDTO> brackets;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BracketDTO {
        private Long bracketId;
        /** HCP o SCRATCH */
        private String scoreType;
        private Integer size;
        /** DRAFT o CONFIRMED */
        private String status;
        /** true si está CONFIRMED y todavía no se jugó ningún partido (se puede revertir a DRAFT). */
        private Boolean canRevertToDraft;
        private List<RoundDTO> rounds;
        /** Clasificados de ese score_type todavía sin ubicar en ningún casillero de ronda 1. */
        private List<PlayerRefDTO> unassignedPlayers;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoundDTO {
        private Integer roundNumber;
        private String roundName;
        private List<SlotDTO> slots;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SlotDTO {
        private Long slotId;
        private Integer slotIndex;
        private Long playerId;
        private String playerName;
        /** Hcp Índice del jugador (Player.handicapIndex), para mostrar junto al nombre. */
        private Double playerHandicapIndex;
        /** Posición del jugador en la Tabla de Play Off (1 = mejor clasificado). Null si no hay jugador. */
        private Integer playerSeed;
        private Boolean isWinner;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlayerRefDTO {
        private Long playerId;
        private String playerName;
        /** Hcp Índice del jugador (Player.handicapIndex), para mostrar junto al nombre. */
        private Double playerHandicapIndex;
        /** Posición del jugador en la Tabla de Play Off (1 = mejor clasificado). Usada para el sembrado ("Cabezas de Serie"). */
        private Integer seed;
    }
}
