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
@Table(name = "tournament_admin_playoff_results", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tournament_admin_id", "player_id", "score_type"},
                name = "uq_playoff_result_admin_player_type")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentAdminPlayoffResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_admin_id", nullable = false)
    private TournamentAdmin tournamentAdmin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    /** HCP (por categoría/neto) o SCRATCH (por gross). */
    @Column(name = "score_type", nullable = false, length = 20)
    @Builder.Default
    private String scoreType = "HCP";

    @Column(name = "total_points", nullable = false)
    @Builder.Default
    private Integer totalPoints = 0;

    @Column(nullable = false)
    private Integer position;

    @Column(nullable = false)
    @Builder.Default
    private Boolean qualified = false;

    /** Solo para modo PER_CATEGORY en torneos CLASICO: id de la categoría del jugador. */
    @Column(name = "category_id")
    private Long categoryId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
