package com.golf.tournament.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NineHoleCourseHandicapCalculatorTest {

    @Test
    void usgaOfficialExample_returnsFour() {
        // Ejemplo oficial de la Regla 6.1b (USGA/R&A Rules of Handicapping):
        // HI 8.7, CR(9H) 35.3, Slope(9H) 121, Par(9H) 36 -> 9H Course Handicap = 4.
        int result = NineHoleCourseHandicapCalculator.calculate(
                new BigDecimal("8.7"), new BigDecimal("35.3"), 121, 36);
        assertEquals(4, result);
    }

    @Test
    void exactHalfTie_roundsUp_regressionForDaugeroCase() {
        // Caso real reportado: jugador con HI 22.5 en tee Blanco Caballeros (CR Ida 35.1,
        // Slope Ida 114, Par Ida 36). La AAG (al 100%) calcula HCP Course = 11. El resultado
        // intermedio de la fórmula es exactamente 10.5 (empate ".5" que debe redondear hacia
        // arriba). Antes de este fix, un truncamiento intermedio en la división slope/113
        // desplazaba el resultado a 10.49999999975, y redondeaba mal a 10.
        int result = NineHoleCourseHandicapCalculator.calculate(
                new BigDecimal("22.5"), new BigDecimal("35.1"), 114, 36);
        assertEquals(11, result);
    }

    @Test
    void negativeHandicap_roundsTowardPositiveInfinityOnTie() {
        // Jugador "plus": Handicap Index negativo. El empate ".5" también debe redondear
        // hacia +infinito (hacia el valor menos negativo), no alejarse de cero.
        // HI -1.0 -> half = -0.5 (ya redondeado). Slope 113 (factor 1), CR 36.0, Par 36
        // -> raw = -0.5 + 0 = -0.5 -> debe redondear a 0, no a -1.
        int result = NineHoleCourseHandicapCalculator.calculate(
                new BigDecimal("-1.0"), new BigDecimal("36.0"), 113, 36);
        assertEquals(0, result);
    }
}
