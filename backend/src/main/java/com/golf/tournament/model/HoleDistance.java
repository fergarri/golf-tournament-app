package com.golf.tournament.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "hole_distances", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"hole_id", "course_tee_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoleDistance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hole_id", nullable = false)
    private Hole hole;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_tee_id", nullable = false)
    private CourseTee courseTee;

    @Column(name = "distancia_yardas", nullable = false)
    private Integer distanciaYardas;
}
