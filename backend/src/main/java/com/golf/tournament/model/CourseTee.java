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

@Entity
@Table(name = "course_tees")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseTee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(length = 50)
    private String grupo;

    /**
     * Género del tee: M = Caballeros, F = Damas.
     */
    @Column(nullable = false, length = 1)
    @Builder.Default
    private String genero = "M";

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    /**
     * Calificación (Course Rating) y Slope Rating de Ida (hoyos 1-9) y Vuelta (hoyos 10-18),
     * específicos de este tee. Se usan para calcular el Course Handicap de 9 hoyos según la
     * Regla 6.1b de las Rules of Handicapping (USGA/R&amp;A). Se cargan importando la planilla
     * "Reporte de Tarjeta" de la AAG.
     */
    @Column(name = "course_rating_ida", precision = 4, scale = 1)
    private BigDecimal courseRatingIda;

    @Column(name = "course_rating_vuelta", precision = 4, scale = 1)
    private BigDecimal courseRatingVuelta;

    @Column(name = "slope_rating_ida")
    private Integer slopeRatingIda;

    @Column(name = "slope_rating_vuelta")
    private Integer slopeRatingVuelta;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
