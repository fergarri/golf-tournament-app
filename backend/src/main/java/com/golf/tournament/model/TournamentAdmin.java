package com.golf.tournament.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tournament_admins")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentAdmin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private LocalDate fecha;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id")
    private Tournament tournament;

    @Column(name = "valor_inscripcion", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal valorInscripcion = BigDecimal.ZERO;

    @Column(name = "cantidad_cuotas", nullable = false)
    @Builder.Default
    private Integer cantidadCuotas = 1;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String estado = "ACTIVE";

    @OneToMany(mappedBy = "tournamentAdmin", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TournamentAdminInscription> inscriptions = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
