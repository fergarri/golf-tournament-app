package com.golf.tournament.security;

import com.golf.tournament.exception.BadRequestException;
import org.springframework.stereotype.Component;

/**
 * Reglas de una contraseña nueva: largo mínimo, letras, mayúscula, número
 * y símbolos opcionales limitados a un conjunto permitido.
 */
@Component
public class PasswordPolicy {

    public static final String ALLOWED_SYMBOLS = "!@#$%&*?+";
    public static final String DISALLOWED_SYMBOL_MESSAGE =
            "Los simbolos disponibles para su contraseña son los siguientes \"!@#$%&*?+\"";

    public void validate(String password) {
        if (password == null || password.isBlank()) {
            throw new BadRequestException("La contraseña es obligatoria");
        }

        for (int i = 0; i < password.length(); i++) {
            if (!isAllowed(password.charAt(i))) {
                throw new BadRequestException(DISALLOWED_SYMBOL_MESSAGE);
            }
        }

        if (password.length() < 6) {
            throw new BadRequestException("La contraseña debe tener al menos 6 caracteres");
        }
        if (!containsAsciiLetter(password)) {
            throw new BadRequestException("La contraseña debe incluir letras");
        }
        if (!containsUppercase(password)) {
            throw new BadRequestException("La contraseña debe contener al menos una letra mayúscula");
        }
        if (!containsDigit(password)) {
            throw new BadRequestException("La contraseña debe contener al menos un número");
        }
    }

    private boolean isAllowed(char c) {
        return isAsciiLetter(c) || isDigit(c) || ALLOWED_SYMBOLS.indexOf(c) >= 0;
    }

    private boolean isAsciiLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean containsAsciiLetter(String password) {
        for (int i = 0; i < password.length(); i++) {
            if (isAsciiLetter(password.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsUppercase(String password) {
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                return true;
            }
        }
        return false;
    }

    private boolean containsDigit(String password) {
        for (int i = 0; i < password.length(); i++) {
            if (isDigit(password.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
