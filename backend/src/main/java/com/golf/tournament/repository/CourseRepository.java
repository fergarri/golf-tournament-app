package com.golf.tournament.repository;

import com.golf.tournament.model.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {
    
    @Query("SELECT c FROM Course c WHERE " +
           "LOWER(c.nombre) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(c.ciudad) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(c.provincia) LIKE LOWER(CONCAT('%', :search, '%'))")
    List<Course> searchCourses(@Param("search") String search);
    
    List<Course> findByPais(String pais);
}
