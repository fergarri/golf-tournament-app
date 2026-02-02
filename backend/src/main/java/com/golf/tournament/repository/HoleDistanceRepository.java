package com.golf.tournament.repository;

import com.golf.tournament.model.HoleDistance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HoleDistanceRepository extends JpaRepository<HoleDistance, Long> {
    
    List<HoleDistance> findByHoleId(Long holeId);
    
    List<HoleDistance> findByCourseTeeId(Long courseTeeId);
    
    Optional<HoleDistance> findByHoleIdAndCourseTeeId(Long holeId, Long courseTeeId);
}
