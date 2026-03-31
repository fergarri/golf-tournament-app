package com.golf.tournament.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tournament_prize_winners")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentPrizeWinner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_prize_id", nullable = false)
    private TournamentPrize prize;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inscription_id", nullable = false)
    private TournamentInscription inscription;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
