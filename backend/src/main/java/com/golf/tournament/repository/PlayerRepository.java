package com.golf.tournament.repository;

import com.golf.tournament.model.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlayerRepository extends JpaRepository<Player, Long> {
    
    Optional<Player> findByMatricula(String matricula);
    
    boolean existsByMatricula(String matricula);
    
    @Query("SELECT p FROM Player p WHERE " +
           "LOWER(p.nombre) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(p.apellido) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(p.matricula) LIKE LOWER(CONCAT('%', :search, '%'))")
    List<Player> searchPlayers(@Param("search") String search);
}
