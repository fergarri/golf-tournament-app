package com.golf.tournament.repository;

import com.golf.tournament.model.TournamentTeeConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TournamentTeeConfigRepository extends JpaRepository<TournamentTeeConfig, Long> {
    
    Optional<TournamentTeeConfig> findByTournamentId(Long tournamentId);
}
