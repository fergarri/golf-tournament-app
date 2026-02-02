package com.golf.tournament.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "holes", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"course_id", "numero_hoyo"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Hole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(name = "numero_hoyo", nullable = false)
    private Integer numeroHoyo;

    @Column(nullable = false)
    private Integer par;

    @Column(nullable = false)
    private Integer handicap;

    @OneToMany(mappedBy = "hole", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<HoleDistance> distances = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
