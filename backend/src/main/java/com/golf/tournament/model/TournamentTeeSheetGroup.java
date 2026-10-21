package com.golf.tournament.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "tournament_tee_sheet_groups", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tee_sheet_id", "published", "sort_order"},
                name = "uq_tee_sheet_group_order")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TournamentTeeSheetGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tee_sheet_id", nullable = false)
    private TournamentTeeSheet teeSheet;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    /**
     * false = borrador editable. true = copia de la última confirmación,
     * que es la que lee la vista pública.
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean published = false;

    @Column(name = "hole_number")
    private Integer holeNumber;

    @Column(name = "tee_time")
    private LocalTime teeTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_1_id")
    private Player player1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_2_id")
    private Player player2;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_3_id")
    private Player player3;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_4_id")
    private Player player4;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
