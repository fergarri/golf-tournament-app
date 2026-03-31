package com.golf.tournament.dto.tournament;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentPrizeDTO {
    private Long id;
    private String prizeType;
    private Long winnerId;
    private Long winnerInscriptionId;
    private String winnerName;
}
