package com.golf.tournament.repository;

import com.golf.tournament.model.TournamentPrize;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TournamentPrizeRepository extends JpaRepository<TournamentPrize, Long> {

    List<TournamentPrize> findByTournamentId(Long tournamentId);

    Optional<TournamentPrize> findByTournamentIdAndPrizeType(Long tournamentId, String prizeType);

    void deleteByTournamentIdAndPrizeType(Long tournamentId, String prizeType);
}
