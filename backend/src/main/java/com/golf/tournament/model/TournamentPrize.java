package com.golf.tournament.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tournament_prizes", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tournament_id", "prize_type"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentPrize {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    @Column(name = "prize_type", nullable = false, length = 50)
    private String prizeType;

    @OneToOne(mappedBy = "prize", cascade = CascadeType.ALL, orphanRemoval = true)
    private TournamentPrizeWinner winner;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
