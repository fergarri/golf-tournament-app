package com.golf.tournament.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "handicap_conversions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HandicapConversion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tee_id", nullable = false)
    private CourseTee tee;

    @Column(name = "hcp_index_from", nullable = false, precision = 4, scale = 1)
    private BigDecimal hcpIndexFrom;

    @Column(name = "hcp_index_to", nullable = false, precision = 4, scale = 1)
    private BigDecimal hcpIndexTo;

    @Column(name = "course_handicap", nullable = false)
    private Integer courseHandicap;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
