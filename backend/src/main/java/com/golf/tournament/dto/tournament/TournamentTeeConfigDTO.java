package com.golf.tournament.dto.tournament;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentTeeConfigDTO {
    
    private Long id;
    private Long courseTeeIdPrimeros9;
    private Long courseTeeIdSegundos9;
}
