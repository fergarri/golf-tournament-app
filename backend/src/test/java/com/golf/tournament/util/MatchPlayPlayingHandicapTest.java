package com.golf.tournament.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MatchPlayPlayingHandicapTest {

    @Test
    void atOneHundredPercent_returnsRoundedCourseHandicap() {
        assertEquals(18, MatchPlayPlayingHandicap.apply(18, new BigDecimal("18.4"), new BigDecimal("100.0")));
        assertEquals(12, MatchPlayPlayingHandicap.apply(12, new BigDecimal("11.6"), new BigDecimal("100")));
    }

    @Test
    void eightyFivePercent_ofRoundedCourseHandicap_roundsOnce() {
        // 18 x 85% = 15.3 -> 15; 12 x 85% = 10.2 -> 10. Diferencia: 5 golpes.
        assertEquals(15, MatchPlayPlayingHandicap.apply(18, null, new BigDecimal("85")));
        assertEquals(10, MatchPlayPlayingHandicap.apply(12, null, new BigDecimal("85.0")));
    }

    @Test
    void eightyFivePercent_usesUnroundedCourseHandicapWhenPresent() {
        // 18.4 x 85% = 15.64 -> 16. No se parte del 18 ya redondeado.
        assertEquals(16, MatchPlayPlayingHandicap.apply(18, new BigDecimal("18.4"), new BigDecimal("85")));
        // 12.4 x 85% = 10.54 -> 11.
        assertEquals(11, MatchPlayPlayingHandicap.apply(12, new BigDecimal("12.4"), new BigDecimal("85")));
    }

    @Test
    void halfTie_roundsTowardPositiveInfinity() {
        // 10 x 85% = 8.5 -> 9.
        assertEquals(9, MatchPlayPlayingHandicap.apply(10, null, new BigDecimal("85")));
        // -2 x 85% = -1.7 -> -2 (más cerca de -2; el ,5 no interviene).
        assertEquals(-2, MatchPlayPlayingHandicap.apply(-2, null, new BigDecimal("85")));
        // -3 x 50% = -1.5 exacto -> -1 (hacia arriba, no alejándose de cero).
        assertEquals(-1, MatchPlayPlayingHandicap.apply(0, new BigDecimal("-3.0"), new BigDecimal("50")));
    }
}
