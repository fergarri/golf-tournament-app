package com.golf.tournament.util;

import com.golf.tournament.exception.BadRequestException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calcula el Course Handicap de 9 hoyos según la Regla 6.1b de las Rules of Handicapping
 * (USGA/R&amp;A, World Handicap System), la misma que aplica la AAG (Argentina).
 *
 * <p>La fórmula exige DOS redondeos, no uno solo:</p>
 * <ol>
 *     <li>Handicap Index x 0,5 se redondea al décimo más cercano (".5 sube").</li>
 *     <li>El resultado completo de la fórmula se redondea al entero más cercano (".5 sube").</li>
 * </ol>
 *
 * <pre>
 * 9H Course Handicap = round1( HandicapIndex x 0.5 ) x ( Slope(9H) / 113 ) + ( CourseRating(9H) - Par(9H) )
 * 9H Course Handicap = round0( resultado anterior )
 * </pre>
 *
 * <p>El Slope Rating, Course Rating y Par de 9 hoyos son específicos del tramo jugado
 * (Ida u hoyos 1-9 en esta app) y NO son necesariamente la mitad exacta de los valores
 * de 18 hoyos, por eso no puede aproximarse dividiendo el Course Handicap de 18 hoyos entre 2.</p>
 *
 * <p>El redondeo ".5 sube" se implementa siempre hacia +infinito (no "alejándose de cero"),
 * para ser correcto también con jugadores "plus" (Handicap Index negativo).</p>
 */
public final class NineHoleCourseHandicapCalculator {

    private static final BigDecimal SLOPE_BASE = BigDecimal.valueOf(113);
    private static final BigDecimal HALF = BigDecimal.valueOf(0.5);

    private NineHoleCourseHandicapCalculator() {
    }

    /**
     * @param handicapIndex   Handicap Index oficial del jugador (18 hoyos).
     * @param courseRatingIda Calificación (Course Rating) de Ida (9 hoyos) del tee jugado.
     * @param slopeRatingIda  Slope Rating de Ida (9 hoyos) del tee jugado.
     * @param parIda          Par de Ida (9 hoyos), normalmente la suma del par de los hoyos 1-9.
     * @return Course Handicap de 9 hoyos, redondeado al entero según la regla oficial.
     */
    public static int calculate(BigDecimal handicapIndex, BigDecimal courseRatingIda, Integer slopeRatingIda, int parIda) {
        if (handicapIndex == null) {
            throw new BadRequestException("El jugador no tiene handicap index asignado.");
        }
        if (courseRatingIda == null || slopeRatingIda == null) {
            throw new BadRequestException(
                    "El tee seleccionado no tiene cargada la Calificación/Slope de Ida (9 hoyos). " +
                            "Importe la planilla de calificación de 9 hoyos del campo antes de continuar.");
        }
        if (parIda <= 0) {
            throw new BadRequestException(
                    "No se pudo determinar el Par de Ida (9 hoyos) de la cancha. Verifique que los hoyos 1 a 9 " +
                            "tengan el par cargado.");
        }

        BigDecimal halfIndex = roundHalfUp(handicapIndex.multiply(HALF), 1);

        // IMPORTANTE: multiplicar primero (halfIndex x slope) y dividir por 113 una sola vez,
        // con suficiente precisión (20 decimales). Si se divide "slope / 113" por separado antes
        // de multiplicar, como 114/113 es un decimal periódico, el truncamiento intermedio puede
        // desplazar en una fracción mínima (ej. 10.49999999975 en vez de 10.5 exacto) un resultado
        // que en realidad es un empate ".5" exacto, haciendo que el redondeo final caiga del lado
        // equivocado (ver caso real: HI 22.5, CR Ida 35.1, Slope Ida 114, Par Ida 36 -> debe ser 11,
        // no 10).
        BigDecimal raw = halfIndex.multiply(BigDecimal.valueOf(slopeRatingIda))
                .divide(SLOPE_BASE, 20, RoundingMode.HALF_UP)
                .add(courseRatingIda.subtract(BigDecimal.valueOf(parIda)));

        return roundHalfUp(raw, 0).intValue();
    }

    /**
     * Redondeo ".5 sube" (hacia +infinito) al número de decimales indicado. A diferencia de
     * {@link RoundingMode#HALF_UP}, que redondea alejándose de cero, este método siempre redondea
     * el empate ".5" hacia el valor superior, tal como exige la regla oficial (relevante para
     * handicaps negativos de jugadores "plus").
     */
    private static BigDecimal roundHalfUp(BigDecimal value, int scale) {
        BigDecimal shifted = value.movePointRight(scale);
        BigDecimal floored = shifted.add(HALF).setScale(0, RoundingMode.FLOOR);
        return floored.movePointLeft(scale).setScale(scale);
    }
}
