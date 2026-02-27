package com.golf.tournament.dto.tournamentadmin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentAdminStageDTO {

    private Long id;
    private Long tournamentAdminId;
    private String nombre;
    private Integer fechasCount;
    private LocalDateTime createdAt;
    private List<Long> tournamentIds;
    private List<TournamentDateDTO> tournaments;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TournamentDateDTO {
        private Long id;
        private String nombre;
        private LocalDate fechaInicio;
        private Boolean doublePoints;
    }
}
