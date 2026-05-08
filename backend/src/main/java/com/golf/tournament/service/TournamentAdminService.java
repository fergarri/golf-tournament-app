package com.golf.tournament.service;

import com.golf.tournament.dto.tournamentadmin.*;
import com.golf.tournament.exception.BadRequestException;
import com.golf.tournament.exception.ResourceNotFoundException;
import com.golf.tournament.model.*;
import com.golf.tournament.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TournamentAdminService {

    private final TournamentAdminRepository tournamentAdminRepository;
    private final TournamentAdminInscriptionRepository inscriptionRepository;
    private final TournamentAdminPaymentRepository paymentRepository;
    private final TournamentInscriptionRepository tournamentInscriptionRepository;
    private final PlayerRepository playerRepository;

    @Transactional
    public TournamentAdminDTO create(CreateTournamentAdminRequest request) {
        TournamentAdmin admin = TournamentAdmin.builder()
                .nombre(request.getNombre())
                .fecha(request.getFecha())
                .tipo(request.getTipo())
                .valorInscripcion(request.getValorInscripcion())
                .cantidadCuotas(request.getCantidadCuotas())
                .build();

        admin = tournamentAdminRepository.save(admin);
        log.info("Torneo administrativo creado: {} tipo={}", admin.getId(), admin.getTipo());
        return convertToDTO(admin);
    }

    @Transactional(readOnly = true)
    public List<TournamentAdminDTO> getAll() {
        return tournamentAdminRepository.findAllByOrderByFechaDesc().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TournamentAdminDTO getById(Long id) {
        TournamentAdmin admin = tournamentAdminRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TournamentAdmin", "id", id));
        return convertToDTO(admin);
    }

    @Transactional
    public TournamentAdminDTO update(Long id, UpdateTournamentAdminRequest request) {
        TournamentAdmin admin = tournamentAdminRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TournamentAdmin", "id", id));

        admin.setNombre(request.getNombre());
        admin.setFecha(request.getFecha());
        admin.setTipo(request.getTipo());
        admin.setValorInscripcion(request.getValorInscripcion());

        boolean cuotasChanged = !admin.getCantidadCuotas().equals(request.getCantidadCuotas());
        admin.setCantidadCuotas(request.getCantidadCuotas());

        if (cuotasChanged) {
            recreatePaymentsForAllInscriptions(admin);
        }

        admin = tournamentAdminRepository.save(admin);
        log.info("Torneo administrativo actualizado: {}", id);
        return convertToDTO(admin);
    }

    @Transactional
    public void delete(Long id) {
        if (!tournamentAdminRepository.existsById(id)) {
            throw new ResourceNotFoundException("TournamentAdmin", "id", id);
        }
        tournamentAdminRepository.deleteById(id);
        log.info("Torneo administrativo eliminado: {}", id);
    }

    @Transactional
    public TournamentAdminDTO finalize(Long id) {
        TournamentAdmin admin = tournamentAdminRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TournamentAdmin", "id", id));
        admin.setEstado("FINALIZED");
        admin = tournamentAdminRepository.save(admin);
        log.info("Torneo administrativo finalizado: {}", id);
        return convertToDTO(admin);
    }

    @Transactional
    public void inscribePlayer(Long tournamentAdminId, Long playerId) {
        TournamentAdmin admin = tournamentAdminRepository.findById(tournamentAdminId)
                .orElseThrow(() -> new ResourceNotFoundException("TournamentAdmin", "id", tournamentAdminId));

        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new ResourceNotFoundException("Player", "id", playerId));

        if (inscriptionRepository.existsByTournamentAdminIdAndPlayerId(tournamentAdminId, playerId)) {
            throw new BadRequestException("El jugador ya está inscripto en el torneo");
        }

        TournamentAdminInscription inscription = TournamentAdminInscription.builder()
                .tournamentAdmin(admin)
                .player(player)
                .build();

        inscription = inscriptionRepository.save(inscription);

        List<TournamentAdminPayment> payments = new ArrayList<>();
        for (int i = 1; i <= admin.getCantidadCuotas(); i++) {
            payments.add(TournamentAdminPayment.builder()
                    .inscription(inscription)
                    .cuotaNumber(i)
                    .pagado(false)
                    .build());
        }
        paymentRepository.saveAll(payments);

        log.info("Jugador {} inscripto en el torneo admin {}", playerId, tournamentAdminId);
    }

    @Transactional
    public void removeInscription(Long inscriptionId) {
        if (!inscriptionRepository.existsById(inscriptionId)) {
            throw new ResourceNotFoundException("TournamentAdminInscription", "id", inscriptionId);
        }
        inscriptionRepository.deleteById(inscriptionId);
        log.info("Inscripción admin eliminada: {}", inscriptionId);
    }

    @Transactional(readOnly = true)
    public TournamentAdminDetailDTO getDetail(Long id) {
        TournamentAdmin admin = tournamentAdminRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TournamentAdmin", "id", id));

        List<TournamentAdminInscription> inscriptions = inscriptionRepository.findByTournamentAdminId(id);

        Long paidCount = paymentRepository.countPaidByTournamentAdminId(id);
        BigDecimal cuotaValue = admin.getCantidadCuotas() > 0
                ? admin.getValorInscripcion().divide(BigDecimal.valueOf(admin.getCantidadCuotas()), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal totalRecaudado = cuotaValue.multiply(BigDecimal.valueOf(paidCount));

        List<TournamentAdminInscriptionDTO> inscriptionDTOs = inscriptions.stream()
                .map(this::convertInscriptionToDTO)
                .sorted(Comparator.comparing(TournamentAdminInscriptionDTO::getPlayerName))
                .collect(Collectors.toList());

        boolean canManageStages = true;

        return TournamentAdminDetailDTO.builder()
                .id(admin.getId())
                .nombre(admin.getNombre())
                .fecha(admin.getFecha())
                .cantidadCuotas(admin.getCantidadCuotas())
                .valorInscripcion(admin.getValorInscripcion())
                .currentInscriptos(inscriptions.size())
                .totalRecaudado(totalRecaudado)
                .canManageStages(canManageStages)
                .inscriptions(inscriptionDTOs)
                .build();
    }

    @Transactional
    public void savePayments(Long tournamentAdminId, SavePaymentsRequest request) {
        if (!tournamentAdminRepository.existsById(tournamentAdminId)) {
            throw new ResourceNotFoundException("TournamentAdmin", "id", tournamentAdminId);
        }

        for (SavePaymentsRequest.PaymentUpdate update : request.getPayments()) {
            TournamentAdminPayment payment = paymentRepository.findById(update.getPaymentId())
                    .orElseThrow(() -> new ResourceNotFoundException("TournamentAdminPayment", "id", update.getPaymentId()));

            if (!payment.getInscription().getTournamentAdmin().getId().equals(tournamentAdminId)) {
                throw new BadRequestException("El pago no pertenece a este torneo administrativo");
            }

            payment.setPagado(update.getPagado());
            paymentRepository.save(payment);
        }

        log.info("Pagos actualizados para torneo admin {}", tournamentAdminId);
    }

    @Transactional
    public ImportAdminInscriptionsResultDTO importInscriptionsToRelatedPendingTournaments(Long tournamentAdminId) {
        TournamentAdmin admin = tournamentAdminRepository.findById(tournamentAdminId)
                .orElseThrow(() -> new ResourceNotFoundException("TournamentAdmin", "id", tournamentAdminId));

        List<TournamentAdminInscription> adminInscriptions = inscriptionRepository.findByTournamentAdminId(tournamentAdminId);

        // Recopilar todos los torneos de las etapas del admin que estén en estado PENDING o IN_PROGRESS
        List<Tournament> pendingRelatedTournaments = admin.getStages().stream()
                .flatMap(s -> s.getTournaments().stream())
                .distinct()
                .filter(t -> "PENDING".equals(t.getEstado()) || "IN_PROGRESS".equals(t.getEstado()))
                .collect(Collectors.toList());

        int importedCount = 0;
        int skippedAlready = 0;
        int skippedByCapacity = 0;

        for (Tournament tournament : pendingRelatedTournaments) {
            Long currentCount = tournamentInscriptionRepository.countByTournamentId(tournament.getId());
            Integer limit = tournament.getLimiteInscriptos();
            int remainingSlots = limit != null ? Math.max(0, limit - currentCount.intValue()) : Integer.MAX_VALUE;

            List<TournamentInscription> toSave = new ArrayList<>();
            for (TournamentAdminInscription adminInscription : adminInscriptions) {
                Long playerId = adminInscription.getPlayer().getId();
                if (tournamentInscriptionRepository.existsByTournamentIdAndPlayerId(tournament.getId(), playerId)) {
                    skippedAlready++;
                    continue;
                }

                if (remainingSlots <= 0) {
                    skippedByCapacity++;
                    continue;
                }

                toSave.add(TournamentInscription.builder()
                        .tournament(tournament)
                        .player(adminInscription.getPlayer())
                        .category(null)
                        .build());
                importedCount++;
                if (limit != null) {
                    remainingSlots--;
                }
            }

            if (!toSave.isEmpty()) {
                tournamentInscriptionRepository.saveAll(toSave);
            }
        }

        log.info("Importación de inscriptos admin {} completada. Torneos de etapas pendientes: {}, importados: {}, repetidos: {}, sin cupo: {}",
                tournamentAdminId, pendingRelatedTournaments.size(), importedCount, skippedAlready, skippedByCapacity);

        return ImportAdminInscriptionsResultDTO.builder()
                .relatedPendingTournaments(pendingRelatedTournaments.size())
                .importedCount(importedCount)
                .skippedAlreadyInscribed(skippedAlready)
                .skippedByCapacity(skippedByCapacity)
                .build();
    }

    /**
     * Exporta los inscriptos de un torneo hacia su Torneo Administrativo asociado.
     * Solo inscribe a los jugadores que todavía no están en el Torneo Administrativo.
     */
    @Transactional
    public ExportTournamentInscriptionsResultDTO exportTournamentInscriptionsToAdmin(Long tournamentId) {
        TournamentAdmin admin = tournamentAdminRepository.findByTournamentInAnyStage(tournamentId)
                .orElseThrow(() -> new BadRequestException("Este torneo no está asociado a ningún Torneo Administrativo"));

        List<TournamentInscription> tournamentInscriptions = tournamentInscriptionRepository.findByTournamentId(tournamentId);

        int imported = 0;
        int skipped = 0;
        List<TournamentAdminInscription> toSave = new ArrayList<>();

        for (TournamentInscription inscription : tournamentInscriptions) {
            Long playerId = inscription.getPlayer().getId();
            if (inscriptionRepository.existsByTournamentAdminIdAndPlayerId(admin.getId(), playerId)) {
                skipped++;
                continue;
            }
            TournamentAdminInscription adminInscription = TournamentAdminInscription.builder()
                    .tournamentAdmin(admin)
                    .player(inscription.getPlayer())
                    .build();
            toSave.add(adminInscription);
            imported++;
        }

        if (!toSave.isEmpty()) {
            List<TournamentAdminInscription> saved = inscriptionRepository.saveAllAndFlush(toSave);
            createPaymentsForNewInscriptions(admin, saved);
        }

        log.info("Exportación de inscriptos del torneo {} al admin {}. Importados: {}, ya existían: {}",
                tournamentId, admin.getId(), imported, skipped);

        return ExportTournamentInscriptionsResultDTO.builder()
                .tournamentAdminId(admin.getId())
                .tournamentAdminNombre(admin.getNombre())
                .importedCount(imported)
                .skippedAlreadyInscribed(skipped)
                .build();
    }

    private void createPaymentsForNewInscriptions(TournamentAdmin admin, List<TournamentAdminInscription> newInscriptions) {
        List<TournamentAdminPayment> newPayments = new ArrayList<>();
        for (TournamentAdminInscription inscription : newInscriptions) {
            for (int i = 1; i <= admin.getCantidadCuotas(); i++) {
                newPayments.add(TournamentAdminPayment.builder()
                        .inscription(inscription)
                        .cuotaNumber(i)
                        .pagado(false)
                        .build());
            }
        }
        if (!newPayments.isEmpty()) {
            paymentRepository.saveAll(newPayments);
        }
    }

    private void recreatePaymentsForAllInscriptions(TournamentAdmin admin) {
        List<TournamentAdminInscription> inscriptions = inscriptionRepository.findByTournamentAdminId(admin.getId());
        for (TournamentAdminInscription inscription : inscriptions) {
            paymentRepository.deleteAll(inscription.getPayments());
            inscription.getPayments().clear();

            List<TournamentAdminPayment> newPayments = new ArrayList<>();
            for (int i = 1; i <= admin.getCantidadCuotas(); i++) {
                newPayments.add(TournamentAdminPayment.builder()
                        .inscription(inscription)
                        .cuotaNumber(i)
                        .pagado(false)
                        .build());
            }
            paymentRepository.saveAll(newPayments);
        }
    }

    private TournamentAdminDTO convertToDTO(TournamentAdmin admin) {
        Long count = tournamentAdminRepository.countInscriptionsByTournamentAdminId(admin.getId());
        Long paidCount = paymentRepository.countPaidByTournamentAdminId(admin.getId());
        BigDecimal cuotaValue = admin.getCantidadCuotas() > 0
                ? admin.getValorInscripcion().divide(BigDecimal.valueOf(admin.getCantidadCuotas()), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal totalRecaudado = cuotaValue.multiply(BigDecimal.valueOf(paidCount));

        return TournamentAdminDTO.builder()
                .id(admin.getId())
                .nombre(admin.getNombre())
                .fecha(admin.getFecha())
                .tipo(admin.getTipo())
                .valorInscripcion(admin.getValorInscripcion())
                .cantidadCuotas(admin.getCantidadCuotas())
                .estado(admin.getEstado())
                .currentInscriptos(count.intValue())
                .totalRecaudado(totalRecaudado)
                .build();
    }

    private TournamentAdminInscriptionDTO convertInscriptionToDTO(TournamentAdminInscription inscription) {
        Player player = inscription.getPlayer();

        List<TournamentAdminInscriptionDTO.PaymentDetailDTO> paymentDTOs = inscription.getPayments().stream()
                .sorted(Comparator.comparingInt(TournamentAdminPayment::getCuotaNumber))
                .map(p -> TournamentAdminInscriptionDTO.PaymentDetailDTO.builder()
                        .paymentId(p.getId())
                        .cuotaNumber(p.getCuotaNumber())
                        .pagado(p.getPagado())
                        .build())
                .collect(Collectors.toList());

        return TournamentAdminInscriptionDTO.builder()
                .inscriptionId(inscription.getId())
                .playerId(player.getId())
                .playerName(player.getApellido() + " " + player.getNombre())
                .matricula(player.getMatricula())
                .telefono(player.getTelefono())
                .email(player.getEmail())
                .payments(paymentDTOs)
                .build();
    }
}
