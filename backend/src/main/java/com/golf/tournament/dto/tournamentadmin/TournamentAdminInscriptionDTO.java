package com.golf.tournament.dto.tournamentadmin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentAdminInscriptionDTO {

    private Long inscriptionId;
    private Long playerId;
    private String playerName;
    private String telefono;
    private String email;
    private List<PaymentDetailDTO> payments;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentDetailDTO {
        private Long paymentId;
        private Integer cuotaNumber;
        private Boolean pagado;
    }
}
