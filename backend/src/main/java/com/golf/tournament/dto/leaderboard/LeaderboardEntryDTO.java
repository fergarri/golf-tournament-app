package com.golf.tournament.dto.leaderboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaderboardEntryDTO {
    
    private Integer position;
    private Long scorecardId;
    private Long playerId;
    private Long inscriptionId; // ID de la inscripción
    private String playerName;
    private String matricula;
    private String clubOrigen;
    private Long categoryId; // ID de la categoría calculada (puede ser null para "Sin Categoría")
    private String categoryName;
    private Integer scoreGross;
    private BigDecimal scoreNeto;
    private Integer totalPar;
    private BigDecimal scoreToPar;
    private BigDecimal handicapCourse;
    private Boolean delivered; // Indica si el jugador entregó su tarjeta
    private Boolean pagado; // Indica si el jugador pagó su inscripción
}
