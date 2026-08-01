package com.golf.tournament.dto.scorecard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScorecardDTO {
    
    private Long id;
    private Long tournamentId;
    private Long playerId;
    private String playerName;
    private Long markerId;
    private String markerName;
    private BigDecimal handicapCourse;
    private Long teeId;
    private Integer cantidadHoyosJuego;
    private String status;
    private LocalDateTime deliveredAt;
    private List<HoleScoreDTO> holeScores;
    private Integer totalScore;
    private Integer totalPar;
    private Boolean marcadorValidado;
    /**
     * Estado (CANCELLED, DISQUALIFIED, etc.) de la tarjeta propia del jugador que estoy marcando.
     * Null si no tengo marcador asignado o esa tarjeta no existe. El frontend lo usa para no bloquear
     * la entrega cuando el jugador marcado canceló o fue descalificado.
     */
    private String markedPlayerScorecardStatus;
}
