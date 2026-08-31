package com.golf.tournament.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tournament_admin_playoff_brackets", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tournament_admin_id", "score_type"},
                name = "uq_playoff_bracket_admin_score_type")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentAdminPlayoffBracket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_admin_id", nullable = false)
    private TournamentAdmin tournamentAdmin;

    /** HCP o SCRATCH — es el único criterio que separa llaves, no hay category_id. */
    @Column(name = "score_type", nullable = false, length = 20)
    private String scoreType;

    /** Cantidad de casilleros en la ronda 1. Siempre potencia de 2. */
    @Column(nullable = false)
    private Integer size;

    /** DRAFT (armando) / CONFIRMED (en juego). */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "DRAFT";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
