package com.golf.tournament.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tournament_admin_scoring_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentAdminScoringConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_admin_id", nullable = false, unique = true)
    private TournamentAdmin tournamentAdmin;

    @Column(name = "birdie_points", nullable = false)
    @Builder.Default
    private Integer birdiePoints = 1;

    @Column(name = "eagle_points", nullable = false)
    @Builder.Default
    private Integer eaglePoints = 5;

    @Column(name = "ace_points", nullable = false)
    @Builder.Default
    private Integer acePoints = 10;

    @Column(name = "participation_points", nullable = false)
    @Builder.Default
    private Integer participationPoints = 1;

    @Column(name = "remaining_positions_points", nullable = false)
    @Builder.Default
    private Integer remainingPositionsPoints = 0;

    @Column(name = "qualified_playoff_positions", nullable = false)
    @Builder.Default
    private Integer qualifiedPlayoffPositions = 8;

    /** Clasificados Sin HCP (Scratch). 0 = no se clasifica, no se calcula. Solo CLASICO. */
    @Column(name = "qualified_playoff_positions_scratch", nullable = false)
    @Builder.Default
    private Integer qualifiedPlayoffPositionsScratch = 0;

    @Column(name = "hcp_qualified_mode", nullable = false, length = 20)
    @Builder.Default
    private String hcpQualifiedMode = "GLOBAL";

    @Column(name = "tie_break_mode", nullable = false, length = 50)
    @Builder.Default
    private String tieBreakMode = "NETO_HCP_HOLE";

    @OneToMany(mappedBy = "scoringConfig", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TournamentAdminScoringPositionPoints> positionPoints = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
