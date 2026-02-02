package com.golf.tournament.repository;

import com.golf.tournament.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    Optional<User> findByEmail(String email);
    
    Optional<User> findByMatricula(String matricula);
    
    boolean existsByEmail(String email);
    
    boolean existsByMatricula(String matricula);
}
