package com.golf.tournament.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tournament_admin_stage_scores", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"stage_id", "player_id", "score_type"},
                name = "uq_stage_score_stage_player_type")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentAdminStageScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stage_id", nullable = false)
    private TournamentAdminStage stage;

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

    private Integer position;

    @Column(name = "tie_break_handicap_index", precision = 4, scale = 1)
    private BigDecimal tieBreakHandicapIndex;

    @Column(name = "last_tournament_score_neto", precision = 10, scale = 2)
    private BigDecimal lastTournamentScoreNeto;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
