package com.golf.tournament.repository;

import com.golf.tournament.model.Scorecard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScorecardRepository extends JpaRepository<Scorecard, Long> {
    
    List<Scorecard> findByTournamentId(Long tournamentId);
    
    List<Scorecard> findByTournamentIdAndDeliveredTrue(Long tournamentId);
    
    Optional<Scorecard> findByTournamentIdAndPlayerId(Long tournamentId, Long playerId);
    
    boolean existsByTournamentIdAndPlayerId(Long tournamentId, Long playerId);
    
    @Query("SELECT s FROM Scorecard s WHERE s.tournament.id = :tournamentId " +
           "AND s.delivered = true ORDER BY s.deliveredAt ASC")
    List<Scorecard> findDeliveredScorecardsByTournament(@Param("tournamentId") Long tournamentId);
}
