package com.golf.tournament.repository;

import com.golf.tournament.model.TournamentAdminPlayoffBracket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TournamentAdminPlayoffBracketRepository extends JpaRepository<TournamentAdminPlayoffBracket, Long> {

    List<TournamentAdminPlayoffBracket> findByTournamentAdminIdOrderByScoreTypeAsc(Long tournamentAdminId);

    Optional<TournamentAdminPlayoffBracket> findByTournamentAdminIdAndScoreType(Long tournamentAdminId, String scoreType);
}
