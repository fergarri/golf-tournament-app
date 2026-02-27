package com.golf.tournament.repository;

import com.golf.tournament.model.TournamentAdminStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface TournamentAdminStageRepository extends JpaRepository<TournamentAdminStage, Long> {

    List<TournamentAdminStage> findByTournamentAdminIdOrderByCreatedAtDesc(Long tournamentAdminId);
    List<TournamentAdminStage> findByTournamentAdminIdOrderByCreatedAtAsc(Long tournamentAdminId);

    Optional<TournamentAdminStage> findByIdAndTournamentAdminId(Long id, Long tournamentAdminId);

    @Query("SELECT t.id FROM TournamentAdminStage s JOIN s.tournaments t " +
            "WHERE s.tournamentAdmin.id = :tournamentAdminId " +
            "AND (:stageId IS NULL OR s.id <> :stageId)")
    Set<Long> findUsedTournamentIdsByAdminExcludingStage(
            @Param("tournamentAdminId") Long tournamentAdminId,
            @Param("stageId") Long stageId
    );
}
