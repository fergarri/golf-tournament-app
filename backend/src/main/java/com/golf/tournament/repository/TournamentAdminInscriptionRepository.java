package com.golf.tournament.repository;

import com.golf.tournament.model.TournamentAdminInscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TournamentAdminInscriptionRepository extends JpaRepository<TournamentAdminInscription, Long> {

    List<TournamentAdminInscription> findByTournamentAdminId(Long tournamentAdminId);

    boolean existsByTournamentAdminIdAndPlayerId(Long tournamentAdminId, Long playerId);

    @Query("SELECT COUNT(i) FROM TournamentAdminInscription i WHERE i.tournamentAdmin.id = :tournamentAdminId")
    Long countByTournamentAdminId(@Param("tournamentAdminId") Long tournamentAdminId);
}
