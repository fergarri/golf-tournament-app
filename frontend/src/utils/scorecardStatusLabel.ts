export interface ScorecardStatusLabel {
  code: string;
  color: string;
}

/**
 * Sigla a mostrar en columnas Gross/Neto/Pos cuando la tarjeta no tiene un score
 * entregado para comparar. `hasScorecard` permite distinguir "sin tarjeta creada"
 * (no se muestra nada) de "tarjeta creada pero no entregada" (NM).
 */
export function getScorecardStatusLabel(
  status?: string,
  hasScorecard = true
): ScorecardStatusLabel | null {
  if (status === 'DISQUALIFIED') return { code: 'DS', color: '#e74c3c' };
  if (status === 'CANCELLED') return { code: 'CA', color: '#7f8c8d' };
  if (!hasScorecard) return null;
  if (status && status !== 'DELIVERED') return { code: 'NM', color: '#f39c12' };
  return null;
}
