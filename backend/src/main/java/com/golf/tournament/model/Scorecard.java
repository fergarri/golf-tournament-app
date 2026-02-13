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
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "scorecards", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tournament_id", "player_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Scorecard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marker_id")
    private Player marker;

    @Column(name = "handicap_course", precision = 4, scale = 2)
    private BigDecimal handicapCourse;

    @Column(nullable = false)
    @Builder.Default
    private Boolean delivered = false;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean canceled = false;

    @OneToMany(mappedBy = "scorecard", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<HoleScore> holeScores = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
