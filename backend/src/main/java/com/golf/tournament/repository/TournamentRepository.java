package com.golf.tournament.repository;

import com.golf.tournament.model.Tournament;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TournamentRepository extends JpaRepository<Tournament, Long> {
    
    Optional<Tournament> findByCodigo(String codigo);
    
    boolean existsByCodigo(String codigo);
    
    @Query("SELECT t FROM Tournament t WHERE " +
           "LOWER(t.nombre) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(t.codigo) LIKE LOWER(CONCAT('%', :search, '%'))")
    List<Tournament> searchTournaments(@Param("search") String search);
    
    List<Tournament> findByTipo(String tipo);
    
    @Query("SELECT t FROM Tournament t ORDER BY t.fechaInicio DESC")
    List<Tournament> findAllOrderByFechaInicioDesc();

    @Query("SELECT t FROM Tournament t WHERE t.id NOT IN (" +
            "SELECT rt.id FROM TournamentAdmin ta JOIN ta.tournaments rt" +
            ") ORDER BY t.fechaInicio DESC")
    List<Tournament> findAvailableForTournamentAdminCreate();

    @Query("SELECT t FROM Tournament t WHERE t.id NOT IN (" +
            "SELECT rt.id FROM TournamentAdmin ta JOIN ta.tournaments rt WHERE ta.id <> :adminId" +
            ") ORDER BY t.fechaInicio DESC")
    List<Tournament> findAvailableForTournamentAdminEdit(@Param("adminId") Long adminId);
}
