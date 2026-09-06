package com.golf.tournament.repository;

import com.golf.tournament.model.TournamentAdminPlayoffMatchCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TournamentAdminPlayoffMatchCardRepository extends JpaRepository<TournamentAdminPlayoffMatchCard, Long> {

    List<TournamentAdminPlayoffMatchCard> findByMatchId(Long matchId);

    Optional<TournamentAdminPlayoffMatchCard> findByMatchIdAndPlayerId(Long matchId, Long playerId);

    List<TournamentAdminPlayoffMatchCard> findByMatchIdIn(List<Long> matchIds);
}
