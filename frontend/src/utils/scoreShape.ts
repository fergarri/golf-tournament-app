// Utilidad para determinar la representación visual (círculo/recuadro) de un golpe
// respecto al par del hoyo, siguiendo la convención clásica de tarjetas de golf:
//   Eagle o mejor (<= -2): círculo doble
//   Birdie (-1):           círculo simple
//   Par (0):                sin marca
//   Bogey (+1):             recuadro simple
//   Doble bogey o peor (>=2): recuadro doble

export type ScoreShapeKind = 'eagle' | 'birdie' | 'par' | 'bogey' | 'doubleBogey' | null;

export type ScoreShapeColorMode = 'semantic' | 'neutral';

export const getScoreShapeKind = (
  golpes: number | null | undefined,
  par: number | null | undefined
): ScoreShapeKind => {
  if (
    golpes === null ||
    golpes === undefined ||
    par === null ||
    par === undefined ||
    !Number.isFinite(golpes) ||
    !Number.isFinite(par)
  ) {
    return null;
  }

  const diff = golpes - par;
  if (diff <= -2) return 'eagle';
  if (diff === -1) return 'birdie';
  if (diff === 0) return 'par';
  if (diff === 1) return 'bogey';
  return 'doubleBogey';
};

/**
 * Devuelve la(s) clase(s) CSS a aplicar sobre el input/span que muestra el golpe.
 * `colorMode`:
 *   - 'semantic': verde para círculos (bajo par), rojo para recuadros (sobre par).
 *   - 'neutral': gris oscuro para cualquier forma (usado en la fila de MARCADOR,
 *     donde el fondo de la celda ya comunica el estado de concordancia).
 */
export const getScoreShapeClassName = (
  kind: ScoreShapeKind,
  colorMode: ScoreShapeColorMode = 'semantic'
): string => {
  if (!kind || kind === 'par') return '';

  const shapeClass =
    kind === 'eagle'
      ? 'score-shape-double-circle'
      : kind === 'birdie'
      ? 'score-shape-circle'
      : kind === 'bogey'
      ? 'score-shape-square'
      : 'score-shape-double-square';

  const colorClass =
    colorMode === 'neutral'
      ? 'score-shape-neutral-color'
      : kind === 'eagle' || kind === 'birdie'
      ? 'score-shape-under-color'
      : 'score-shape-over-color';

  return `${shapeClass} ${colorClass}`;
};
