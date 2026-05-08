package com.golf.tournament.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tournament_admin_scoring_position_points")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentAdminScoringPositionPoints {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scoring_config_id", nullable = false)
    private TournamentAdminScoringConfig scoringConfig;

    @Column(name = "position", nullable = false)
    private Integer position;

    @Column(name = "points", nullable = false)
    private Integer points;
}
