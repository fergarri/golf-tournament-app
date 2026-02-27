package com.golf.tournament.repository;

import com.golf.tournament.model.TournamentAdminStageScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TournamentAdminStageScoreRepository extends JpaRepository<TournamentAdminStageScore, Long> {

    List<TournamentAdminStageScore> findByStageIdOrderByPositionAsc(Long stageId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM TournamentAdminStageScore s WHERE s.stage.id = :stageId")
    void deleteByStageId(@Param("stageId") Long stageId);
}
