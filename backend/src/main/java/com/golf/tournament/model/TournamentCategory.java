package com.golf.tournament.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tournament_categories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(name = "handicap_min", nullable = false, precision = 4, scale = 1)
    private BigDecimal handicapMin;

    @Column(name = "handicap_max", nullable = false, precision = 4, scale = 1)
    private BigDecimal handicapMax;

    // No cascade to prevent deletion of inscriptions when category is deleted
    // Inscriptions will remain with category = null
    @OneToMany(mappedBy = "category")
    @Builder.Default
    private List<TournamentInscription> inscriptions = new ArrayList<>();
}
