package com.golf.tournament.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "frutales_scores", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tournament_id", "scorecard_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FrutalesScore {

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
