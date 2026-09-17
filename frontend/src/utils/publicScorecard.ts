const VIEWABLE_STATUSES: Set<string> = new Set(['DELIVERED', 'CANCELLED', 'DISQUALIFIED']);

export function canViewPublicScorecard(
  status?: string | null,
  scorecardId?: number | null
): boolean {
  return Boolean(scorecardId && status && VIEWABLE_STATUSES.has(status));
}

export function formatScoreToPar(toPar: number): string {
  const rounded = Math.abs(toPar - Math.round(toPar)) < 0.05 ? Math.round(toPar) : toPar;
  if (rounded === 0) return 'E';
  if (typeof rounded === 'number' && rounded > 0) {
    return Number.isInteger(rounded) ? `+${rounded}` : `+${rounded.toFixed(1)}`;
  }
  return Number.isInteger(rounded) ? `${rounded}` : rounded.toFixed(1);
}

export function formatNeto(neto: number): string {
  return Number.isInteger(neto) ? String(neto) : neto.toFixed(1);
}
