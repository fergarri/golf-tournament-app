package com.golf.tournament.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tournament_tee_configs", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"tournament_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentTeeConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_tee_id_primeros_9", nullable = false)
    private CourseTee courseTeeIdPrimeros9;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_tee_id_segundos_9")
    private CourseTee courseTeeIdSegundos9;
}
