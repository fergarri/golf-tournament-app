package com.golf.tournament.security;

import com.golf.tournament.config.CustomUserDetails;
import com.golf.tournament.model.Role;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Punto único para conocer el usuario autenticado actual y validar el acceso a
 * los datos de su club. Los superadmins (rol ADMIN) no tienen club asignado y
 * ven/administran todos los clubes; el resto de los usuarios queda acotado a
 * su propio club (courseId).
 */
@Component
public class CurrentUserProvider {

    public CustomUserDetails getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            return null;
        }
        return (CustomUserDetails) authentication.getPrincipal();
    }

    public boolean isSuperAdmin() {
        CustomUserDetails user = getCurrentUser();
        return user != null && user.isSuperAdmin();
    }

    public Long getCurrentCourseId() {
        CustomUserDetails user = getCurrentUser();
        return user != null ? user.getCourseId() : null;
    }

    public Role getCurrentRole() {
        CustomUserDetails user = getCurrentUser();
        return user != null ? user.getRole() : null;
    }

    /**
     * Valida que el recurso identificado por courseId pertenezca al club del usuario actual.
     * Los superadmins tienen acceso a cualquier club.
     */
    public void assertClubAccess(Long courseId) {
        if (isSuperAdmin()) {
            return;
        }
        Long currentCourseId = getCurrentCourseId();
        if (currentCourseId == null || courseId == null || !currentCourseId.equals(courseId)) {
            throw new AccessDeniedException("No tiene permisos para acceder a los datos de otro club");
        }
    }

    /**
     * Los usuarios con rol USER pueden crear y editar players, torneos, torneos
     * administrativos, campo, tees y hoyos, pero no eliminarlos. ADMIN y ADMIN_CLUB
     * sí pueden eliminar estos recursos.
     */
    public void assertCanDelete() {
        if (getCurrentRole() == Role.USER) {
            throw new AccessDeniedException("Su rol no tiene permisos para eliminar este recurso");
        }
    }
}
