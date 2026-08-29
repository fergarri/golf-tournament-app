package com.golf.tournament.dto.course;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HandicapConversionDTO {
    private Long id;
    private BigDecimal hcpIndexFrom;
    private BigDecimal hcpIndexTo;
    private Integer courseHandicap;
}
