export const ALLOWED_PASSWORD_SYMBOLS = '!@#$%&*?+';

export const DISALLOWED_SYMBOL_MESSAGE =
  `Los simbolos disponibles para su contraseña son los siguientes "${ALLOWED_PASSWORD_SYMBOLS}"`;

export const PASSWORD_HINT =
  `La contraseña debe tener al menos 6 caracteres, incluir letras, al menos una mayúscula y al menos un número. No puede ser igual a la actual. Los símbolos son opcionales y solo pueden ser "${ALLOWED_PASSWORD_SYMBOLS}".`;

const isAsciiLetter = (c: string) => (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
const isDigit = (c: string) => c >= '0' && c <= '9';

/** Devuelve el mensaje de error, o null si la contraseña cumple el formato. */
export function validateNewPassword(password: string): string | null {
  if (!password || !password.trim()) {
    return 'La contraseña es obligatoria';
  }

  for (const c of password) {
    if (!isAsciiLetter(c) && !isDigit(c) && !ALLOWED_PASSWORD_SYMBOLS.includes(c)) {
      return DISALLOWED_SYMBOL_MESSAGE;
    }
  }

  if (password.length < 6) {
    return 'La contraseña debe tener al menos 6 caracteres';
  }

  let hasLetter = false;
  let hasUpper = false;
  let hasDigit = false;
  for (const c of password) {
    if (isAsciiLetter(c)) hasLetter = true;
    if (c >= 'A' && c <= 'Z') hasUpper = true;
    if (isDigit(c)) hasDigit = true;
  }

  if (!hasLetter) return 'La contraseña debe incluir letras';
  if (!hasUpper) return 'La contraseña debe contener al menos una letra mayúscula';
  if (!hasDigit) return 'La contraseña debe contener al menos un número';
  return null;
}
