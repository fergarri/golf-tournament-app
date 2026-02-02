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
@Table(name = "tournament_inscriptions", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tournament_id", "player_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentInscription {

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
    @JoinColumn(name = "category_id")
    private TournamentCategory category;

    @Column(name = "handicap_course", precision = 4, scale = 1)
    private BigDecimal handicapCourse;

    @Column(name = "fecha_inscripcion", nullable = false)
    @Builder.Default
    private LocalDateTime fechaInscripcion = LocalDateTime.now();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
