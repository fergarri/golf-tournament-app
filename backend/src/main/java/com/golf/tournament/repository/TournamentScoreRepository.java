package com.golf.tournament.repository;

import com.golf.tournament.model.TournamentScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TournamentScoreRepository extends JpaRepository<TournamentScore, Long> {

    List<TournamentScore> findByTournamentIdAndScoreTypeOrderByTotalPointsDesc(Long tournamentId, String scoreType);

    List<TournamentScore> findByTournamentIdOrderByTotalPointsDesc(Long tournamentId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM TournamentScore ts WHERE ts.tournament.id = :tournamentId AND ts.scoreType = :scoreType")
    void deleteAllByTournamentIdAndScoreType(@Param("tournamentId") Long tournamentId, @Param("scoreType") String scoreType);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM TournamentScore ts WHERE ts.tournament.id = :tournamentId")
    void deleteAllByTournamentId(@Param("tournamentId") Long tournamentId);
}
