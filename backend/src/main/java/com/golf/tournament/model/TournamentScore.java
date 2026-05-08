package com.golf.tournament.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tournament_scores", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tournament_id", "scorecard_id", "score_type"},
        name = "uq_tournament_scores_tournament_scorecard_score_type")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentScore {

    /** Valor para torneos Frutales (puntaje global de la fecha). */
    public static final String SCORE_TYPE_GLOBAL = "GLOBAL";

    /** Valor para torneos Clásico: puntaje por categoría (basado en score neto). */
    public static final String SCORE_TYPE_CATEGORY = "CATEGORY";

    /** Valor para torneos Clásico: puntaje scratch (basado en score gross). */
    public static final String SCORE_TYPE_SCRATCH = "SCRATCH";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scorecard_id", nullable = false)
    private Scorecard scorecard;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    /** GLOBAL, CATEGORY o SCRATCH */
    @Column(name = "score_type", nullable = false, length = 20)
    @Builder.Default
    private String scoreType = SCORE_TYPE_GLOBAL;

    /** Solo para CATEGORY: id de la categoría del jugador en el torneo. */
    @Column(name = "category_id")
    private Long categoryId;

    private Integer position;

    @Column(name = "position_points", nullable = false)
    @Builder.Default
    private Integer positionPoints = 0;

    @Column(name = "birdie_count", nullable = false)
    @Builder.Default
    private Integer birdieCount = 0;

    @Column(name = "birdie_points", nullable = false)
    @Builder.Default
    private Integer birdiePoints = 0;

    @Column(name = "eagle_count", nullable = false)
    @Builder.Default
    private Integer eagleCount = 0;

    @Column(name = "eagle_points", nullable = false)
    @Builder.Default
    private Integer eaglePoints = 0;

    @Column(name = "ace_count", nullable = false)
    @Builder.Default
    private Integer aceCount = 0;

    @Column(name = "ace_points", nullable = false)
    @Builder.Default
    private Integer acePoints = 0;

    @Column(name = "participation_points", nullable = false)
    @Builder.Default
    private Integer participationPoints = 0;

    @Column(name = "total_points", nullable = false)
    @Builder.Default
    private Integer totalPoints = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
