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
 * Golpes de UN jugador en UN hoyo (o hoyo extra de muerte súbita) de un partido de
 * Match Play. El "marcador" siempre es el rival del partido: golpesRival es lo que el
 * dueño de la tarjeta cargó sobre su rival para ese hoyo (validación cruzada visual).
 */
@Entity
@Table(name = "tournament_admin_playoff_match_hole_scores", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"card_id", "hole_sequence"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentAdminPlayoffMatchHoleScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "card_id", nullable = false)
    private TournamentAdminPlayoffMatchCard card;

    @Column(name = "hole_sequence", nullable = false)
    private Integer holeSequence;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hole_id", nullable = false)
    private Hole hole;

    @Column(name = "golpes_propio")
    private Integer golpesPropio;

    @Column(name = "golpes_rival")
    private Integer golpesRival;

    @Column(nullable = false)
    @Builder.Default
    private Boolean validado = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
