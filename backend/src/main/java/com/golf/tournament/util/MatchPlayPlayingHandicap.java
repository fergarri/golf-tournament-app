package com.golf.tournament.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Handicap de juego del Match Play.
 *
 * <p>Al 100% el resultado es el HCP Course ya redondeado. Con otro porcentaje se multiplica
 * el HCP Course sin redondear (si está disponible) y se redondea una sola vez al entero más
 * cercano, con el ,5 hacia arriba. Si solo se tiene el entero (tabla de 18 hoyos), el
 * porcentaje se aplica a ese entero y se usa el mismo redondeo.</p>
 */
public final class MatchPlayPlayingHandicap {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private MatchPlayPlayingHandicap() {
    }

    public static int apply(int roundedCourseHandicap, BigDecimal unroundedCourseHandicap, BigDecimal allowancePercent) {
        if (allowancePercent == null || allowancePercent.compareTo(ONE_HUNDRED) == 0) {
            return roundedCourseHandicap;
        }
        BigDecimal base = unroundedCourseHandicap != null
                ? unroundedCourseHandicap
                : BigDecimal.valueOf(roundedCourseHandicap);
        BigDecimal raw = base.multiply(allowancePercent)
                .divide(ONE_HUNDRED, 20, RoundingMode.HALF_UP);
        return NineHoleCourseHandicapCalculator.roundHalfTowardPositiveInfinity(raw);
    }
}
