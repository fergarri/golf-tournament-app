package com.golf.tournament.repository;

import com.golf.tournament.model.HoleScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HoleScoreRepository extends JpaRepository<HoleScore, Long> {
    
    List<HoleScore> findByScorecardId(Long scorecardId);
    
    Optional<HoleScore> findByScorecardIdAndHoleId(Long scorecardId, Long holeId);
    
    @Query("SELECT hs FROM HoleScore hs WHERE hs.scorecard.id = :scorecardId " +
           "AND (hs.golpesPropio IS NULL OR hs.golpesMarcador IS NULL " +
           "OR hs.golpesPropio <> hs.golpesMarcador)")
    List<HoleScore> findUnvalidatedScoresByScorecardId(@Param("scorecardId") Long scorecardId);
}
