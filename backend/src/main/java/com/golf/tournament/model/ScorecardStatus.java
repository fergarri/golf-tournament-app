package com.golf.tournament.model;

public enum ScorecardStatus {
    PENDING_CONFIG,
    IN_PROGRESS,
    DELIVERED,
    CANCELLED,
    DISQUALIFIED,
    /**
     * Tarjeta de un jugador con hcp_activo=false (handicap inactivo). Se asigna al
     * entregar la tarjeta o al finalizar el torneo. No participa en la puntuación
     * ni en las posiciones, aunque sí puede cargarse y verse su score.
     */
    INACTIVE
}
