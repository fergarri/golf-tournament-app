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

@Entity
@Table(name = "players")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 100)
    private String apellido;

    private String email;

    @Column(nullable = false, unique = true, length = 50)
    private String matricula;

    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;

    @Column(nullable = false, length = 1)
    private String sexo;

    @Column(name = "handicap_index", precision = 4, scale = 1)
    private BigDecimal handicapIndex;

    @Column(length = 50)
    private String telefono;

    @Column(name = "club_origen")
    private String clubOrigen;

    /**
     * Club (course) resuelto automáticamente a partir de clubOrigen. Se usa solo para
     * poder filtrar "jugadores de mi club" en la UI; no restringe el acceso al jugador,
     * que sigue siendo visible en toda la app.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    /**
     * Estado del handicap del jugador. Si es false, el jugador puede inscribirse,
     * cargar y entregar su tarjeta, pero no participa en la puntuación ni en las
     * posiciones de los torneos (su tarjeta pasa a estado INACTIVE al entregarla
     * o al finalizar el torneo).
     */
    @Column(name = "hcp_activo", nullable = false)
    @Builder.Default
    private Boolean hcpActivo = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
