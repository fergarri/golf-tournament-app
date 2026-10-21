package com.golf.tournament.repository;

import com.golf.tournament.model.TournamentInscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TournamentInscriptionRepository extends JpaRepository<TournamentInscription, Long> {
    
    List<TournamentInscription> findByTournamentId(Long tournamentId);
    
    List<TournamentInscription> findByPlayerId(Long playerId);
    
    Optional<TournamentInscription> findByTournamentIdAndPlayerId(Long tournamentId, Long playerId);
    
    boolean existsByTournamentIdAndPlayerId(Long tournamentId, Long playerId);
    
    @Query("SELECT COUNT(ti) FROM TournamentInscription ti WHERE ti.tournament.id = :tournamentId")
    Long countByTournamentId(@Param("tournamentId") Long tournamentId);
    
    List<TournamentInscription> findByTournamentIdAndCategoryId(Long tournamentId, Long categoryId);

    @Query("SELECT i FROM TournamentInscription i JOIN FETCH i.player WHERE i.tournament.id = :tournamentId")
    List<TournamentInscription> findByTournamentIdWithPlayer(@Param("tournamentId") Long tournamentId);
}
