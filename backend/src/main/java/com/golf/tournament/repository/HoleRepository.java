package com.golf.tournament.repository;

import com.golf.tournament.model.Hole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HoleRepository extends JpaRepository<Hole, Long> {
    
    List<Hole> findByCourseIdOrderByNumeroHoyoAsc(Long courseId);
    
    Optional<Hole> findByCourseIdAndNumeroHoyo(Long courseId, Integer numeroHoyo);
}
