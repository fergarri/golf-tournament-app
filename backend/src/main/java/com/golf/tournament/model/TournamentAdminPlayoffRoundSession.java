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
 * Habilita, con un único código, la carga de partidos de Match Play de una ronda de una
 * llave de playoff. Ver docs/diseno-match-play-playoff.md.
 */
@Entity
@Table(name = "tournament_admin_playoff_round_sessions", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"bracket_id", "round_number"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentAdminPlayoffRoundSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bracket_id", nullable = false)
    private TournamentAdminPlayoffBracket bracket;

    @Column(name = "round_number", nullable = false)
    private Integer roundNumber;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    /** OPEN / CLOSED */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "OPEN";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tee_masculino_id")
    private CourseTee teeMasculino;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tee_femenino_id")
    private CourseTee teeFemenino;

    @Column(name = "cantidad_hoyos_juego", nullable = false)
    private Integer cantidadHoyosJuego;

    @Column(name = "started_at", nullable = false)
    @Builder.Default
    private LocalDateTime startedAt = LocalDateTime.now();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
