package com.golf.tournament.security;

import com.golf.tournament.config.CustomUserDetails;
import com.golf.tournament.model.Permission;
import com.golf.tournament.model.Role;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Ejecuta procesos automáticos del sistema (schedulers, jobs internos) con acceso total,
 * simulando un usuario "de sistema" (superadmin) en el contexto de seguridad.
 *
 * Los @Scheduled corren en un hilo de fondo sin ningún usuario autenticado: cualquier
 * validación de {@link CurrentUserProvider} (assertClubAccess, assertCanDelete, etc.)
 * fallaría siempre en ese contexto porque no hay Authentication en el SecurityContext.
 * Un proceso automático no es la acción de un usuario particular, así que no debe quedar
 * sujeto a las restricciones de club/rol de un usuario: se ejecuta como sistema.
 */
@Component
public class SystemProcessRunner {

    private static final String SYSTEM_USERNAME = "system";

    public void run(Runnable action) {
        Authentication previous = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.getContext().setAuthentication(systemAuthentication());
        try {
            action.run();
        } finally {
            SecurityContextHolder.getContext().setAuthentication(previous);
        }
    }

    private Authentication systemAuthentication() {
        List<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + Role.ADMIN.name()),
                new SimpleGrantedAuthority(Permission.TOTAL.name())
        );
        CustomUserDetails systemUser = new CustomUserDetails(
                SYSTEM_USERNAME, "", authorities, null, Role.ADMIN, null, null);
        return new UsernamePasswordAuthenticationToken(systemUser, null, authorities);
    }
}
