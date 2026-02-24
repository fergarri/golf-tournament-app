package com.golf.tournament.repository;

import com.golf.tournament.model.FrutalesScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FrutalesScoreRepository extends JpaRepository<FrutalesScore, Long> {

    List<FrutalesScore> findByTournamentIdOrderByTotalPointsDesc(Long tournamentId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM FrutalesScore fs WHERE fs.tournament.id = :tournamentId")
    void deleteAllByTournamentId(@Param("tournamentId") Long tournamentId);
}
