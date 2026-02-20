package com.golf.tournament.dto.tournamentadmin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SavePaymentsRequest {

    private List<PaymentUpdate> payments;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentUpdate {
        private Long paymentId;
        private Boolean pagado;
    }
}
