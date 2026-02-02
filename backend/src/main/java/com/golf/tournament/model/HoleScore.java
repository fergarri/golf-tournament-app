package com.golf.tournament.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "hole_scores", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"scorecard_id", "hole_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoleScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scorecard_id", nullable = false)
    private Scorecard scorecard;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hole_id", nullable = false)
    private Hole hole;

    @Column(name = "golpes_propio")
    private Integer golpesPropio;

    @Column(name = "golpes_marcador")
    private Integer golpesMarcador;

    @Transient
    public Boolean isValidado() {
        if (golpesPropio != null && golpesMarcador != null) {
            return golpesPropio.equals(golpesMarcador);
        }
        return false;
    }
}
