package com.golf.tournament.repository;

import com.golf.tournament.model.TournamentAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface TournamentAdminRepository extends JpaRepository<TournamentAdmin, Long> {

    List<TournamentAdmin> findAllByOrderByFechaDesc();

    @Query("SELECT COUNT(i) FROM TournamentAdminInscription i WHERE i.tournamentAdmin.id = :tournamentAdminId")
    Long countInscriptionsByTournamentAdminId(@Param("tournamentAdminId") Long tournamentAdminId);

    @Query("SELECT t.id FROM TournamentAdmin ta JOIN ta.tournaments t " +
            "WHERE t.id IN :tournamentIds AND (:adminId IS NULL OR ta.id <> :adminId)")
    Set<Long> findConflictingRelatedTournamentIds(
            @Param("tournamentIds") List<Long> tournamentIds,
            @Param("adminId") Long adminId
    );
}
