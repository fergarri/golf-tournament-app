package com.golf.tournament.repository;

import com.golf.tournament.model.TournamentAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TournamentAdminRepository extends JpaRepository<TournamentAdmin, Long> {

    List<TournamentAdmin> findAllByOrderByFechaDesc();

    @Query("SELECT COUNT(i) FROM TournamentAdminInscription i WHERE i.tournamentAdmin.id = :tournamentAdminId")
    Long countInscriptionsByTournamentAdminId(@Param("tournamentAdminId") Long tournamentAdminId);
}
