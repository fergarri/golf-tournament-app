package com.golf.tournament.repository;

import com.golf.tournament.model.TournamentAdminScoringConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TournamentAdminScoringConfigRepository extends JpaRepository<TournamentAdminScoringConfig, Long> {

    Optional<TournamentAdminScoringConfig> findByTournamentAdminId(Long tournamentAdminId);

    boolean existsByTournamentAdminId(Long tournamentAdminId);
}
