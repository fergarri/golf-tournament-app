export function formatCurrency(value: number | undefined | null): string {
  if (value === undefined || value === null) return '0,00';
  return value.toLocaleString('es-AR', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

export function parseCurrency(text: string): number {
  if (!text) return 0;
  const cleaned = text.replace(/\./g, '').replace(',', '.');
  const num = parseFloat(cleaned);
  return isNaN(num) ? 0 : num;
}
