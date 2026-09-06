package com.golf.tournament.dto.tournamentadmin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayoffMatchAccessResponseDTO {
    private Long matchId;
    private String tournamentAdminName;
    /** HCP / SCRATCH */
    private String scoreType;
    private String roundName;
    private Long playerId;
    private String playerName;
    private Long opponentId;
    private String opponentName;
}
