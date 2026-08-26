package com.golf.tournament.model;

import java.util.Set;

public enum Role {

    /** Superadmin: ve y administra todos los clubes. */
    ADMIN(Set.of(Permission.TOTAL)),
    /**
     * Admin de club (nivel intermedio): igual que USER, pero además puede crear y
     * gestionar usuarios (roles USER y ADMIN_CLUB) de su propio club. A diferencia
     * de USER, sí puede eliminar players, torneos, torneos administrativos, tees y hoyos.
     */
    ADMIN_CLUB(Set.of(Permission.GAMES, Permission.ADMINISTRATION, Permission.CLUB_USERS)),
    /**
     * Admin de club (nivel básico): acotado a los torneos, torneos administrativos y
     * cancha de su propio club. Puede crear y editar players, torneos, torneos
     * administrativos, campo, tees y hoyos, pero no eliminarlos.
     */
    USER(Set.of(Permission.GAMES, Permission.ADMINISTRATION));

    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) {
        this.permissions = permissions;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }
}
