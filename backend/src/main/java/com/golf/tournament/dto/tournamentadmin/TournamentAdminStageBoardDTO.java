package com.golf.tournament.dto.tournamentadmin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentAdminStageBoardDTO {

    private Long stageId;
    private Long tournamentAdminId;
    private String stageName;
    private LocalDateTime stageCreatedAt;
    /** FRUTALES o CLASICO */
    private String tipo;
    private List<TournamentDateColumnDTO> tournaments;
    /** Filas de puntos Con HCP. Para FRUTALES es el único listado. Para CLASICO no se usa. */
    private List<PlayerStageRowDTO> rows;
    /** Filas de puntos Sin HCP (scratch). Solo poblado para tipo CLASICO. */
    private List<PlayerStageRowDTO> scratchRows;
    /** Filas agrupadas por categoría. Solo poblado para tipo CLASICO. */
    private List<CategoryRowsDTO> categoryRows;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TournamentDateColumnDTO {
        private Long tournamentId;
        private String tournamentName;
        private LocalDate fechaInicio;
        private Boolean doublePoints;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryRowsDTO {
        private Long categoryId;
        private String categoryName;
        private BigDecimal handicapMin;
        private BigDecimal handicapMax;
        private List<PlayerStageRowDTO> rows;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlayerStageRowDTO {
        private Long playerId;
        private String playerName;
        private BigDecimal handicapIndex;
        private Integer totalPoints;
        private Integer position;
        private Map<Long, Integer> pointsByTournament;
    }
}
