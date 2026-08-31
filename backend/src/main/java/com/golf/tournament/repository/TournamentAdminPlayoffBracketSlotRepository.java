package com.golf.tournament.repository;

import com.golf.tournament.model.TournamentAdminPlayoffBracketSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TournamentAdminPlayoffBracketSlotRepository extends JpaRepository<TournamentAdminPlayoffBracketSlot, Long> {

    @Query("SELECT s FROM TournamentAdminPlayoffBracketSlot s LEFT JOIN FETCH s.player " +
            "WHERE s.bracket.id = :bracketId ORDER BY s.roundNumber ASC, s.slotIndex ASC")
    List<TournamentAdminPlayoffBracketSlot> findByBracketIdOrderByRoundNumberAscSlotIndexAsc(@Param("bracketId") Long bracketId);

    Optional<TournamentAdminPlayoffBracketSlot> findByBracketIdAndRoundNumberAndSlotIndex(
            Long bracketId, Integer roundNumber, Integer slotIndex);

    boolean existsByBracketIdAndIsWinnerTrue(Long bracketId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM TournamentAdminPlayoffBracketSlot s WHERE s.bracket.id = :bracketId")
    void deleteByBracketId(@Param("bracketId") Long bracketId);
}
