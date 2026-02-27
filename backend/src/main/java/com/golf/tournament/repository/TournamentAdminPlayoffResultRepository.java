package com.golf.tournament.repository;

import com.golf.tournament.model.TournamentAdminPlayoffResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TournamentAdminPlayoffResultRepository extends JpaRepository<TournamentAdminPlayoffResult, Long> {

    List<TournamentAdminPlayoffResult> findByTournamentAdminIdOrderByPositionAsc(Long tournamentAdminId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM TournamentAdminPlayoffResult r WHERE r.tournamentAdmin.id = :tournamentAdminId")
    void deleteByTournamentAdminId(@Param("tournamentAdminId") Long tournamentAdminId);
}
