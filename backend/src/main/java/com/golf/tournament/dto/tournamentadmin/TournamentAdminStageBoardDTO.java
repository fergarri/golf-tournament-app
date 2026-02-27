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
    private List<TournamentDateColumnDTO> tournaments;
    private List<PlayerStageRowDTO> rows;

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
    public static class PlayerStageRowDTO {
        private Long playerId;
        private String playerName;
        private BigDecimal handicapIndex;
        private Integer totalPoints;
        private Integer position;
        private Map<Long, Integer> pointsByTournament;
    }
}
