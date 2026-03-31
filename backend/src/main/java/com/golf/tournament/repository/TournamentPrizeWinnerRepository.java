package com.golf.tournament.repository;

import com.golf.tournament.model.TournamentPrizeWinner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TournamentPrizeWinnerRepository extends JpaRepository<TournamentPrizeWinner, Long> {

    Optional<TournamentPrizeWinner> findByPrizeId(Long prizeId);

    void deleteByPrizeId(Long prizeId);
}
