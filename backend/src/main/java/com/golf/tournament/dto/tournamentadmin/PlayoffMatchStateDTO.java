package com.golf.tournament.dto.tournamentadmin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Estado completo de un partido de Match Play, para la tarjeta virtual pública.
 * Ver docs/diseno-match-play-playoff.md.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayoffMatchStateDTO {

    private Long matchId;
    /** IN_PROGRESS / FINISHED */
    private String status;
    /** HCP / SCRATCH */
    private String scoreType;
    private Integer cantidadHoyosJuego;
    private String resultSummary;
    private Long winnerPlayerId;
    /** Id del jugador que hizo el pedido (para que el frontend sepa cuál lado es "yo"). */
    private Long requestingPlayerId;

    private PlayerSideDTO playerA;
    private PlayerSideDTO playerB;

    private List<HoleInfoDTO> holes;

    private TallyDTO tally;

    private Boolean canDeliver;
    private String blockedReason;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlayerSideDTO {
        private Long playerId;
        private String playerName;
        /** Inicial del nombre + apellido (ej: "N. Trachta"), para usar como título de fila. */
        private String shortName;
        /** Nombre del tee desde el que juega (ej: "Blanco", "Rojo"). Puede ser null. */
        private String teeName;
        /** null si la llave es SCRATCH */
        private Integer handicapCourse;
        /** IN_PROGRESS / DELIVERED / CANCELLED */
        private String cardStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HoleInfoDTO {
        private Integer holeSequence;
        private Integer numeroHoyo;
        private Integer par;
        private Integer handicapIndex;
        /** Distancia en yardas del hoyo según el tee de cada jugador (null si no hay tee/distancia cargada). */
        private Integer distanceA;
        private Integer distanceB;
        /** Golpes de hándicap que recibe cada jugador en este hoyo (0, 1 o más). */
        private Integer strokesA;
        private Integer strokesB;
        private Integer golpesPropioA;
        private Integer golpesRivalA;
        private Boolean validadoA;
        private Integer golpesPropioB;
        private Integer golpesRivalB;
        private Boolean validadoB;
        /** "A" / "B" / "HALVED" / null si todavía no está definido */
        private String holeWinner;
        /** true si este hoyo todavía no existe en la BD (próximo hoyo de muerte súbita a habilitar) */
        private Boolean pending;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TallyDTO {
        private Integer holesWonA;
        private Integer holesWonB;
        private Integer holesHalved;
        private Integer holesPlayed;
        private Boolean decided;
        private Long leaderPlayerId;
        private Integer margin;
        private Integer holesRemaining;
    }
}
