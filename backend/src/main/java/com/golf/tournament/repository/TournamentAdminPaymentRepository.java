package com.golf.tournament.repository;

import com.golf.tournament.model.TournamentAdminPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TournamentAdminPaymentRepository extends JpaRepository<TournamentAdminPayment, Long> {

    List<TournamentAdminPayment> findByInscriptionId(Long inscriptionId);

    @Query("SELECT p FROM TournamentAdminPayment p WHERE p.inscription.tournamentAdmin.id = :tournamentAdminId")
    List<TournamentAdminPayment> findByTournamentAdminId(@Param("tournamentAdminId") Long tournamentAdminId);

    @Query("SELECT COUNT(p) FROM TournamentAdminPayment p " +
           "WHERE p.inscription.tournamentAdmin.id = :tournamentAdminId AND p.pagado = true")
    Long countPaidByTournamentAdminId(@Param("tournamentAdminId") Long tournamentAdminId);
}
