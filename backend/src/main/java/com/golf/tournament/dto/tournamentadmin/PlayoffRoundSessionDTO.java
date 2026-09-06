package com.golf.tournament.dto.tournamentadmin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Sesión de ronda de Match Play (código habilitante) con el resumen en vivo de sus partidos.
 * Ver docs/diseno-match-play-playoff.md.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayoffRoundSessionDTO {

    private Long roundSessionId;
    private Integer roundNumber;
    private String code;
    /** OPEN / CLOSED */
    private String status;
    private Long teeMasculinoId;
    private String teeMasculinoName;
    private Long teeFemeninoId;
    private String teeFemeninoName;
    private Integer cantidadHoyosJuego;
    private List<MatchSummaryDTO> matches;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MatchSummaryDTO {
        private Long matchId;
        private Long topSlotId;
        private Long bottomSlotId;
        private Long playerAId;
        private String playerAName;
        private Long playerBId;
        private String playerBName;
        /** IN_PROGRESS / FINISHED */
        private String status;
        private Integer holesWonA;
        private Integer holesWonB;
        private Integer holesPlayed;
        private String resultSummary;
        private Long winnerPlayerId;
        /** Texto corto para el chip en la llave, ej. "Pérez 2 arriba · hoyo 6" o "Igualados · hoyo 9". Null si FINISHED (usar resultSummary). */
        private String liveStatusLabel;
    }
}
