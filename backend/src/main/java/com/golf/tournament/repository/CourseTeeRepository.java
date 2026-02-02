package com.golf.tournament.repository;

import com.golf.tournament.model.CourseTee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseTeeRepository extends JpaRepository<CourseTee, Long> {
    
    List<CourseTee> findByCourseId(Long courseId);
    
    List<CourseTee> findByCourseIdAndActiveTrue(Long courseId);
    
    List<CourseTee> findByCourseIdAndGrupo(Long courseId, String grupo);
}
