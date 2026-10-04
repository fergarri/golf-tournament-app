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

/**
 * Partido de Match Play entre dos casilleros hermanos de una ronda de la llave.
 * Ver docs/diseno-match-play-playoff.md.
 */
@Entity
@Table(name = "tournament_admin_playoff_matches", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"round_session_id", "top_slot_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentAdminPlayoffMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "round_session_id", nullable = false)
    private TournamentAdminPlayoffRoundSession roundSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "top_slot_id", nullable = false)
    private TournamentAdminPlayoffBracketSlot topSlot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bottom_slot_id", nullable = false)
    private TournamentAdminPlayoffBracketSlot bottomSlot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_a_id", nullable = false)
    private Player playerA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_b_id", nullable = false)
    private Player playerB;

    /** IN_PROGRESS / FINISHED */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "IN_PROGRESS";

    /**
     * STROKES: partidos ya empezados, se cargan los golpes.
     * WINNER: partidos nuevos, se marca quién ganó el hoyo.
     */
    @Column(name = "entry_mode", nullable = false, length = 20)
    @Builder.Default
    private String entryMode = "STROKES";

    /**
     * Porcentaje de HCP Course congelado la primera vez que un jugador abre la tarjeta.
     * Null hasta ese momento, y en partidos Scratch o de carga por golpes.
     */
    @Column(name = "hcp_allowance_percent", precision = 5, scale = 1)
    private BigDecimal hcpAllowancePercent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_player_id")
    private Player winnerPlayer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_slot_id")
    private TournamentAdminPlayoffBracketSlot winnerSlot;

    @Column(name = "result_summary", length = 50)
    private String resultSummary;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
