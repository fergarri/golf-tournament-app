package com.golf.tournament.repository;

import com.golf.tournament.model.TournamentTeeSheetGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TournamentTeeSheetGroupRepository extends JpaRepository<TournamentTeeSheetGroup, Long> {

    @Query("""
            SELECT g FROM TournamentTeeSheetGroup g
            LEFT JOIN FETCH g.player1
            LEFT JOIN FETCH g.player2
            LEFT JOIN FETCH g.player3
            LEFT JOIN FETCH g.player4
            WHERE g.teeSheet.id = :teeSheetId AND g.published = :published
            ORDER BY g.sortOrder ASC
            """)
    List<TournamentTeeSheetGroup> findFetchedByTeeSheetIdAndPublished(
            @Param("teeSheetId") Long teeSheetId,
            @Param("published") boolean published);

    boolean existsByTeeSheetIdAndPublished(Long teeSheetId, boolean published);
}
