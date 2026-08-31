package com.golf.tournament.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Casillero de una llave de playoff. La ronda siguiente se arma solo por posición: el
 * casillero {@code slotIndex = k} de la ronda {@code roundNumber + 1} es el ganador del
 * partido entre {@code slotIndex = 2k} y {@code slotIndex = 2k + 1} de la ronda
 * {@code roundNumber}. No se guardan relaciones explícitas entre rondas.
 */
@Entity
@Table(name = "tournament_admin_playoff_bracket_slots", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"bracket_id", "round_number", "slot_index"},
                name = "uq_playoff_bracket_slot")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentAdminPlayoffBracketSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bracket_id", nullable = false)
    private TournamentAdminPlayoffBracket bracket;

    /** 1 = primera ronda (la que se arma a mano), 2, 3... hasta la final. */
    @Column(name = "round_number", nullable = false)
    private Integer roundNumber;

    /** Posición dentro de la ronda, 0-based. */
    @Column(name = "slot_index", nullable = false)
    private Integer slotIndex;

    /** null = vacío (sin asignar todavía, o BYE). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id")
    private Player player;

    @Column(name = "is_winner", nullable = false)
    @Builder.Default
    private Boolean isWinner = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
