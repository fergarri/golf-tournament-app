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

    @Query("SELECT s FROM TournamentAdminStageScore s JOIN FETCH s.player WHERE s.stage.id = :stageId ORDER BY s.position ASC")
    List<TournamentAdminStageScore> findByStageIdOrderByPositionAsc(@Param("stageId") Long stageId);

    @Query("SELECT s FROM TournamentAdminStageScore s JOIN FETCH s.player WHERE s.stage.id = :stageId AND s.scoreType = :scoreType ORDER BY s.position ASC")
    List<TournamentAdminStageScore> findByStageIdAndScoreTypeOrderByPositionAsc(@Param("stageId") Long stageId, @Param("scoreType") String scoreType);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM TournamentAdminStageScore s WHERE s.stage.id = :stageId")
    void deleteByStageId(@Param("stageId") Long stageId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM TournamentAdminStageScore s WHERE s.stage.id = :stageId AND s.scoreType = :scoreType")
    void deleteByStageIdAndScoreType(@Param("stageId") Long stageId, @Param("scoreType") String scoreType);
}
