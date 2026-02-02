package com.golf.tournament.repository;

import com.golf.tournament.model.TournamentCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface TournamentCategoryRepository extends JpaRepository<TournamentCategory, Long> {
    
    List<TournamentCategory> findByTournamentId(Long tournamentId);
    
    @Query("SELECT tc FROM TournamentCategory tc WHERE tc.tournament.id = :tournamentId " +
           "AND :handicapIndex >= tc.handicapMin AND :handicapIndex <= tc.handicapMax")
    Optional<TournamentCategory> findCategoryForHandicap(
        @Param("tournamentId") Long tournamentId,
        @Param("handicapIndex") BigDecimal handicapIndex
    );
}
