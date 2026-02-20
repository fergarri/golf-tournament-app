package com.golf.tournament.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tournament_admin_payments", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"inscription_id", "cuota_number"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentAdminPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inscription_id", nullable = false)
    private TournamentAdminInscription inscription;

    @Column(name = "cuota_number", nullable = false)
    private Integer cuotaNumber;

    @Column(nullable = false)
    @Builder.Default
    private Boolean pagado = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
