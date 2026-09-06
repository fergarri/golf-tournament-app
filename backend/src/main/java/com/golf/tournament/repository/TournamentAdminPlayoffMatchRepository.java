package com.golf.tournament.repository;

import com.golf.tournament.model.TournamentAdminPlayoffMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TournamentAdminPlayoffMatchRepository extends JpaRepository<TournamentAdminPlayoffMatch, Long> {

    List<TournamentAdminPlayoffMatch> findByRoundSessionId(Long roundSessionId);

    Optional<TournamentAdminPlayoffMatch> findByIdAndRoundSessionId(Long id, Long roundSessionId);

    List<TournamentAdminPlayoffMatch> findByRoundSessionIdIn(List<Long> roundSessionIds);
}
