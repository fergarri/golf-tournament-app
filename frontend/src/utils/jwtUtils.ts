/**
 * Verifica si un token JWT ha expirado
 * @param token - El token JWT a verificar
 * @returns true si el token está expirado o es inválido, false si aún es válido
 */
export const isTokenExpired = (token: string): boolean => {
  try {
    // Decodificar el payload (segunda parte del JWT: header.payload.signature)
    const payload = JSON.parse(atob(token.split('.')[1]));
    
    // exp viene en segundos, convertir a milisegundos
    const expirationTime = payload.exp * 1000;
    
    // Comparar con la fecha actual
    return Date.now() >= expirationTime;
  } catch (error) {
    // Si hay error al decodificar, considerar el token como inválido/expirado
    console.error('Error al decodificar el token:', error);
    return true;
  }
};

/**
 * Obtiene la fecha de expiración de un token JWT
 * @param token - El token JWT
 * @returns Date de expiración o null si no se puede decodificar
 */
export const getTokenExpiration = (token: string): Date | null => {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return new Date(payload.exp * 1000);
  } catch (error) {
    console.error('Error al obtener fecha de expiración:', error);
    return null;
  }
};
