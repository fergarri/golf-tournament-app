package com.golf.tournament.repository;

import com.golf.tournament.model.TournamentAdminPlayoffMatchHoleScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TournamentAdminPlayoffMatchHoleScoreRepository extends JpaRepository<TournamentAdminPlayoffMatchHoleScore, Long> {

    List<TournamentAdminPlayoffMatchHoleScore> findByCardIdOrderByHoleSequenceAsc(Long cardId);

    Optional<TournamentAdminPlayoffMatchHoleScore> findByCardIdAndHoleSequence(Long cardId, Integer holeSequence);

    boolean existsByCardIdInAndGolpesPropioIsNotNull(List<Long> cardIds);

    boolean existsByCardIdInAndHoleResultIsNotNull(List<Long> cardIds);
}
