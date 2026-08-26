package com.golf.tournament.model;

public enum Permission {
    TOTAL,
    GAMES,
    ADMINISTRATION,
    /** Permite gestionar (crear/editar/eliminar) usuarios del propio club (roles USER y ADMIN_CLUB). */
    CLUB_USERS
}
