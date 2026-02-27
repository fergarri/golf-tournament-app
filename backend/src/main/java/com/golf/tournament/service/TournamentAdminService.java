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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TournamentAdminService {

    private final TournamentAdminRepository tournamentAdminRepository;
    private final TournamentAdminInscriptionRepository inscriptionRepository;
    private final TournamentAdminPaymentRepository paymentRepository;
    private final TournamentRepository tournamentRepository;
    private final PlayerRepository playerRepository;

    @Transactional
    public TournamentAdminDTO create(CreateTournamentAdminRequest request) {
        List<Tournament> relatedTournaments = resolveRelatedTournaments(request.getRelatedTournamentIds(), null);

        TournamentAdmin admin = TournamentAdmin.builder()
                .nombre(request.getNombre())
                .fecha(request.getFecha())
                .valorInscripcion(request.getValorInscripcion())
                .cantidadCuotas(request.getCantidadCuotas())
                .tournaments(relatedTournaments)
                .build();

        admin = tournamentAdminRepository.save(admin);
        log.info("Torneo administrativo creado: {}", admin.getId());
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

    @Transactional(readOnly = true)
    public List<TournamentRelationOptionDTO> getRelationOptions(Long adminId) {
        if (adminId == null) {
            return tournamentRepository.findAvailableForTournamentAdminCreate().stream()
                    .map(t -> TournamentRelationOptionDTO.builder()
                            .id(t.getId())
                            .nombre(t.getNombre())
                            .fechaInicio(t.getFechaInicio())
                            .related(false)
                            .build())
                    .collect(Collectors.toList());
        }

        TournamentAdmin admin = tournamentAdminRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("TournamentAdmin", "id", adminId));
        final Set<Long> currentRelatedIds = admin.getTournaments().stream()
                .map(Tournament::getId)
                .collect(Collectors.toSet());
        List<Tournament> available = tournamentRepository.findAvailableForTournamentAdminEdit(adminId);

        return available.stream()
                .map(t -> TournamentRelationOptionDTO.builder()
                        .id(t.getId())
                        .nombre(t.getNombre())
                        .fechaInicio(t.getFechaInicio())
                        .related(currentRelatedIds.contains(t.getId()))
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public TournamentAdminDTO update(Long id, UpdateTournamentAdminRequest request) {
        TournamentAdmin admin = tournamentAdminRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TournamentAdmin", "id", id));

        admin.setNombre(request.getNombre());
        admin.setFecha(request.getFecha());
        admin.setValorInscripcion(request.getValorInscripcion());
        admin.setTournaments(resolveRelatedTournaments(request.getRelatedTournamentIds(), id));

        boolean cuotasChanged = !admin.getCantidadCuotas().equals(request.getCantidadCuotas());
        admin.setCantidadCuotas(request.getCantidadCuotas());

        if (cuotasChanged) {
            recreatePaymentsForAllInscriptions(admin);
        }

        admin = tournamentAdminRepository.save(admin);
        log.info("Torneo administrativo actualizado: {}", admin.getId());
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
            throw new BadRequestException("El jugador ya está inscrito en este torneo");
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

        log.info("Jugador {} inscrito en torneo admin {}", playerId, tournamentAdminId);
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
        boolean canManageStages = admin.getTournaments() != null
                && !admin.getTournaments().isEmpty()
                && admin.getTournaments().stream().anyMatch(t -> "FRUTALES".equals(t.getTipo()));

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
        List<Tournament> relatedTournaments = admin.getTournaments() != null ? admin.getTournaments() : Collections.emptyList();
        String relatedNames = relatedTournaments.stream()
                .map(Tournament::getNombre)
                .sorted()
                .collect(Collectors.joining(", "));

        return TournamentAdminDTO.builder()
                .id(admin.getId())
                .nombre(admin.getNombre())
                .fecha(admin.getFecha())
                .tournamentNombre(relatedNames.isBlank() ? null : relatedNames)
                .relatedTournamentIds(relatedTournaments.stream().map(Tournament::getId).collect(Collectors.toList()))
                .relatedTournaments(relatedTournaments.stream()
                        .map(t -> TournamentAdminDTO.RelatedTournamentDTO.builder()
                                .id(t.getId())
                                .nombre(t.getNombre())
                                .build())
                        .collect(Collectors.toList()))
                .valorInscripcion(admin.getValorInscripcion())
                .cantidadCuotas(admin.getCantidadCuotas())
                .estado(admin.getEstado())
                .currentInscriptos(count.intValue())
                .totalRecaudado(totalRecaudado)
                .build();
    }

    private List<Tournament> resolveRelatedTournaments(List<Long> relatedTournamentIds, Long adminId) {
        if (relatedTournamentIds == null || relatedTournamentIds.isEmpty()) {
            return new ArrayList<>();
        }

        Set<Long> uniqueIds = new HashSet<>(relatedTournamentIds);
        List<Tournament> tournaments = tournamentRepository.findAllById(uniqueIds);
        if (tournaments.size() != uniqueIds.size()) {
            throw new BadRequestException("Uno o más torneos relacionados no existen");
        }

        Set<Long> conflictingIds = tournamentAdminRepository.findConflictingRelatedTournamentIds(
                new ArrayList<>(uniqueIds),
                adminId
        );
        if (!conflictingIds.isEmpty()) {
            throw new BadRequestException("Hay torneos ya relacionados con otro torneo administrativo");
        }

        return tournaments;
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
                .telefono(player.getTelefono())
                .email(player.getEmail())
                .payments(paymentDTOs)
                .build();
    }
}
