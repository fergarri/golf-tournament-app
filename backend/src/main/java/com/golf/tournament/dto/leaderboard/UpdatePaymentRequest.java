package com.golf.tournament.dto.leaderboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePaymentRequest {
    
    private List<PaymentUpdate> payments;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentUpdate {
        private Long inscriptionId;
        private Boolean pagado;
    }
}
