package com.golf.tournament.repository;

import com.golf.tournament.model.Tournament;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
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

    @Query("SELECT t FROM Tournament t WHERE t.course.id = :courseId ORDER BY t.fechaInicio DESC")
    List<Tournament> findByCourseIdOrderByFechaInicioDesc(@Param("courseId") Long courseId);

    @Query("SELECT t FROM Tournament t WHERE t.tipo = :tipo AND t.id NOT IN (" +
            "SELECT st.id FROM TournamentAdminStage s JOIN s.tournaments st" +
            ") ORDER BY t.fechaInicio DESC")
    List<Tournament> findAvailableForStageByTipo(@Param("tipo") String tipo);

    @Query("SELECT t FROM Tournament t WHERE t.tipo = :tipo AND t.id NOT IN (" +
            "SELECT st.id FROM TournamentAdminStage s JOIN s.tournaments st WHERE s.id <> :stageId" +
            ") ORDER BY t.fechaInicio DESC")
    List<Tournament> findAvailableForStageByTipoExcludingStage(@Param("tipo") String tipo, @Param("stageId") Long stageId);

    /** Torneos asociados a una etapa administrativa, ordenados por fechaInicio desc. */
    @Query("SELECT t FROM TournamentAdminStage stage JOIN stage.tournaments t WHERE stage.id = :stageId ORDER BY t.fechaInicio DESC")
    List<Tournament> findByStageIdOrderByFechaInicioDesc(@Param("stageId") Long stageId);

    /**
     * Torneos en progreso cuyo horario de cierre ya fue alcanzado.
     * Cubre:
     *  - Torneos de días anteriores (fechaInicio < hoy) que quedaron sin cerrarse.
     *  - Torneos de hoy (fechaInicio = hoy) cuyo horarioCierre <= hora actual.
     */
    @Query("SELECT t FROM Tournament t WHERE t.estado = 'IN_PROGRESS' " +
           "AND t.horarioCierre IS NOT NULL " +
           "AND (t.fechaInicio < :today " +
           "     OR (t.fechaInicio = :today AND t.horarioCierre <= :now))")
    List<Tournament> findTournamentsToAutoClose(@Param("today") LocalDate today,
                                               @Param("now") LocalTime now);

    /**
     * Torneos pendientes cuyo horario de inicio ya fue alcanzado.
     * Cubre:
     *  - Torneos de días anteriores (fechaInicio < hoy) que quedaron sin iniciarse.
     *  - Torneos de hoy (fechaInicio = hoy) cuyo horarioInicio <= hora actual.
     */
    @Query("SELECT t FROM Tournament t WHERE t.estado = 'PENDING' " +
           "AND (t.fechaInicio < :today " +
           "     OR (t.fechaInicio = :today AND t.horarioInicio <= :now))")
    List<Tournament> findTournamentsToAutoStart(@Param("today") LocalDate today,
                                                @Param("now") LocalTime now);
}
