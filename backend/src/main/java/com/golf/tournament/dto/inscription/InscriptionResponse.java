package com.golf.tournament.dto.inscription;

import com.golf.tournament.dto.player.PlayerDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InscriptionResponse {
    
    private Long inscriptionId;
    private PlayerDTO player;
    private String categoryName;
    private BigDecimal handicapCourse;
    private String message;
}
