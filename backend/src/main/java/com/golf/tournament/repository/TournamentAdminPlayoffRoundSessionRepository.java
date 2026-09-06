package com.golf.tournament.repository;

import com.golf.tournament.model.TournamentAdminPlayoffRoundSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TournamentAdminPlayoffRoundSessionRepository extends JpaRepository<TournamentAdminPlayoffRoundSession, Long> {

    Optional<TournamentAdminPlayoffRoundSession> findByBracketIdAndRoundNumber(Long bracketId, Integer roundNumber);

    List<TournamentAdminPlayoffRoundSession> findByBracketIdOrderByRoundNumberAsc(Long bracketId);

    Optional<TournamentAdminPlayoffRoundSession> findByCode(String code);

    boolean existsByCode(String code);

    void deleteByBracketId(Long bracketId);
}
