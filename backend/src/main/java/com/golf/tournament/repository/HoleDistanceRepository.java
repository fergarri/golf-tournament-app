package com.golf.tournament.repository;

import com.golf.tournament.model.HoleDistance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HoleDistanceRepository extends JpaRepository<HoleDistance, Long> {
    
    List<HoleDistance> findByHoleId(Long holeId);
    
    List<HoleDistance> findByCourseTeeId(Long courseTeeId);
    
    Optional<HoleDistance> findByHoleIdAndCourseTeeId(Long holeId, Long courseTeeId);

    @Modifying
    @Query("DELETE FROM HoleDistance d WHERE d.courseTee.id = :teeId")
    void deleteByCourseTeeId(@Param("teeId") Long teeId);
}
