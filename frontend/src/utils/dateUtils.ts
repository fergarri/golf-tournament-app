/**
 * Formatea una fecha en formato YYYY-MM-DD a DD/MM/YYYY sin conversión de zona horaria
 * @param dateString Fecha en formato YYYY-MM-DD
 * @returns Fecha formateada como DD/MM/YYYY
 */
export const formatLocalDate = (dateString: string): string => {
  if (!dateString) return '';
  
  // Parsear la fecha como fecha local (sin conversión de zona horaria)
  const [year, month, day] = dateString.split('T')[0].split('-');
  
  return `${day}/${month}/${year}`;
};

/**
 * Formatea una fecha en formato ISO a formato local argentino sin conversión de zona horaria
 * @param dateString Fecha en formato ISO
 * @returns Fecha formateada como DD/MM/YYYY
 */
export const formatDateSafe = (dateString: string): string => {
  if (!dateString) return '';
  
  // Extraer solo la parte de la fecha (YYYY-MM-DD)
  const datePart = dateString.split('T')[0];
  const [year, month, day] = datePart.split('-');
  
  // Formatear manualmente sin usar Date para evitar problemas de timezone
  return `${day.padStart(2, '0')}/${month.padStart(2, '0')}/${year}`;
};
