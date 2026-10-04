package com.golf.tournament.dto.tournamentadmin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveScoringConfigRequest {

    @NotNull
    @Min(0)
    private Integer birdiePoints;

    @NotNull
    @Min(0)
    private Integer eaglePoints;

    @NotNull
    @Min(0)
    private Integer acePoints;

    @NotNull
    @Min(0)
    private Integer participationPoints;

    @NotNull
    @Min(0)
    private Integer remainingPositionsPoints;

    @NotNull
    @Min(1)
    private Integer qualifiedPlayoffPositions;

    /** Clasificados Sin HCP (Scratch). 0 = sin clasificación. Solo CLASICO. */
    @NotNull
    @Min(0)
    private Integer qualifiedPlayoffPositionsScratch;

    /** GLOBAL o PER_CATEGORY. */
    @NotBlank
    private String hcpQualifiedMode;

    @NotBlank
    private String tieBreakMode;

    /** Si es null, se conserva false. */
    private Boolean discountUnderPar;

    /** FIRST_PLACE o ALL_UNDER_PAR. Si el descuento está activo y llega vacío, se usa FIRST_PLACE. */
    private String underParDiscountMode;

    /** Porcentaje de HCP Course del Match Play. Si es null, se usa 100. Entre 0 y 100, un decimal. */
    private BigDecimal matchPlayHcpPercent;

    @NotNull
    private List<PositionPointsRequest> positionPoints;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PositionPointsRequest {
        @NotNull
        @Min(1)
        private Integer position;

        @NotNull
        @Min(0)
        private Integer points;
    }
}
