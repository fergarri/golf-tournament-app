package com.golf.tournament.repository;

import com.golf.tournament.model.TournamentTeeSheet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TournamentTeeSheetRepository extends JpaRepository<TournamentTeeSheet, Long> {

    Optional<TournamentTeeSheet> findByTournamentId(Long tournamentId);
}
