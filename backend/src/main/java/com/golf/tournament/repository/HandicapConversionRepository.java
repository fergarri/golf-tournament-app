package com.golf.tournament.repository;

import com.golf.tournament.model.HandicapConversion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface HandicapConversionRepository extends JpaRepository<HandicapConversion, Long> {

    @Query("SELECT hc FROM HandicapConversion hc " +
           "WHERE hc.tee.id = :teeId " +
           "AND :handicapIndex >= hc.hcpIndexFrom " +
           "AND :handicapIndex <= hc.hcpIndexTo")
    Optional<HandicapConversion> findByTeeAndHandicapIndex(
        @Param("teeId") Long teeId,
        @Param("handicapIndex") BigDecimal handicapIndex
    );
}
