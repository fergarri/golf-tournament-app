package com.golf.tournament.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tournament_admin_inscriptions", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tournament_admin_id", "player_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentAdminInscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_admin_id", nullable = false)
    private TournamentAdmin tournamentAdmin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @Column(name = "fecha_inscripcion", nullable = false)
    @Builder.Default
    private LocalDateTime fechaInscripcion = LocalDateTime.now();

    @OneToMany(mappedBy = "inscription", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TournamentAdminPayment> payments = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
