package com.golf.tournament.service;

import com.golf.tournament.dto.tournamentadmin.SaveBracketSlotsRequest;
import com.golf.tournament.dto.tournamentadmin.ScoringConfigDTO;
import com.golf.tournament.dto.tournamentadmin.TournamentAdminPlayoffBracketsDTO;
import com.golf.tournament.exception.BadRequestException;
import com.golf.tournament.exception.ResourceNotFoundException;
import com.golf.tournament.model.Player;
import com.golf.tournament.model.TournamentAdmin;
import com.golf.tournament.model.TournamentAdminPlayoffBracket;
import com.golf.tournament.model.TournamentAdminPlayoffBracketSlot;
import com.golf.tournament.model.TournamentAdminPlayoffResult;
import com.golf.tournament.repository.PlayerRepository;
import com.golf.tournament.repository.TournamentAdminPlayoffBracketRepository;
import com.golf.tournament.repository.TournamentAdminPlayoffBracketSlotRepository;
import com.golf.tournament.repository.TournamentAdminPlayoffResultRepository;
import com.golf.tournament.repository.TournamentAdminRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TournamentAdminPlayoffBracketService {

    private static final List<String> VALID_SCORE_TYPES = List.of("HCP", "SCRATCH");

    private final TournamentAdminRepository tournamentAdminRepository;
    private final TournamentAdminPlayoffBracketRepository bracketRepository;
    private final TournamentAdminPlayoffBracketSlotRepository slotRepository;
    private final TournamentAdminPlayoffResultRepository playoffResultRepository;
    private final TournamentAdminScoringConfigService scoringConfigService;
    private final PlayerRepository playerRepository;

    @Transactional(readOnly = true)
    public TournamentAdminPlayoffBracketsDTO getBrackets(Long tournamentAdminId) {
        TournamentAdmin admin = getAdminOrThrow(tournamentAdminId);
        boolean scratchApplicable = isScratchApplicable(admin);

        List<TournamentAdminPlayoffBracket> brackets = bracketRepository
                .findByTournamentAdminIdOrderByScoreTypeAsc(tournamentAdminId);

        List<TournamentAdminPlayoffBracketsDTO.BracketDTO> bracketDTOs = brackets.stream()
                .map(this::toBracketDTO)
                .collect(Collectors.toList());

        return TournamentAdminPlayoffBracketsDTO.builder()
                .tournamentAdminId(tournamentAdminId)
                .tipo(admin.getTipo())
                .scratchApplicable(scratchApplicable)
                .brackets(bracketDTOs)
                .build();
    }

    @Transactional
    public TournamentAdminPlayoffBracketsDTO generate(Long tournamentAdminId, String scoreTypeFilter) {
        TournamentAdmin admin = getAdminOrThrow(tournamentAdminId);
        boolean scratchApplicable = isScratchApplicable(admin);

        List<String> scoreTypesToGenerate = new ArrayList<>();
        if (scoreTypeFilter != null) {
            String normalized = scoreTypeFilter.trim().toUpperCase();
            if (!VALID_SCORE_TYPES.contains(normalized)) {
                throw new BadRequestException("scoreType inválido: debe ser HCP o SCRATCH");
            }
            if (normalized.equals("SCRATCH") && !scratchApplicable) {
                throw new BadRequestException("Este torneo no tiene clasificación Scratch configurada");
            }
            scoreTypesToGenerate.add(normalized);
        } else {
            scoreTypesToGenerate.add("HCP");
            if (scratchApplicable) {
                scoreTypesToGenerate.add("SCRATCH");
            }
        }

        for (String scoreType : scoreTypesToGenerate) {
            generateSingleBracket(admin, scoreType);
        }

        return getBrackets(tournamentAdminId);
    }

    private void generateSingleBracket(TournamentAdmin admin, String scoreType) {
        Long tournamentAdminId = admin.getId();
        Optional<TournamentAdminPlayoffBracket> existing = bracketRepository
                .findByTournamentAdminIdAndScoreType(tournamentAdminId, scoreType);

        if (existing.isPresent() && "CONFIRMED".equals(existing.get().getStatus())) {
            log.info("Llave {} de torneo {} ya está CONFIRMED, no se regenera", scoreType, tournamentAdminId);
            return;
        }

        List<TournamentAdminPlayoffResult> qualified = playoffResultRepository
                .findByTournamentAdminIdAndScoreTypeOrderByPositionAsc(tournamentAdminId, scoreType)
                .stream()
                .filter(TournamentAdminPlayoffResult::getQualified)
                .sorted(Comparator.comparing(TournamentAdminPlayoffResult::getPosition))
                .collect(Collectors.toList());

        if (qualified.size() < 2) {
            throw new BadRequestException(
                    "No hay suficientes clasificados (" + qualified.size() + ") para armar la llave " + scoreType +
                            ". Calculá los puntos de Play Off primero.");
        }

        int size = nextPowerOfTwo(qualified.size());

        TournamentAdminPlayoffBracket bracket;
        if (existing.isPresent()) {
            bracket = existing.get();
            slotRepository.deleteByBracketId(bracket.getId());
            bracket.setSize(size);
            bracket.setStatus("DRAFT");
            bracket = bracketRepository.save(bracket);
        } else {
            bracket = bracketRepository.save(TournamentAdminPlayoffBracket.builder()
                    .tournamentAdmin(admin)
                    .scoreType(scoreType)
                    .size(size)
                    .status("DRAFT")
                    .build());
        }

        // Todos los casilleros (incluidos los de la ronda 1) arrancan vacíos: los clasificados
        // quedan en el panel "Sin ubicar" hasta que el admin los arrastre a mano a la llave.
        int totalRounds = log2(size);
        List<TournamentAdminPlayoffBracketSlot> slotsToCreate = new ArrayList<>();
        for (int round = 1; round <= totalRounds; round++) {
            int slotsInRound = size / (int) Math.pow(2, round - 1);
            for (int index = 0; index < slotsInRound; index++) {
                slotsToCreate.add(TournamentAdminPlayoffBracketSlot.builder()
                        .bracket(bracket)
                        .roundNumber(round)
                        .slotIndex(index)
                        .isWinner(false)
                        .build());
            }
        }
        slotRepository.saveAll(slotsToCreate);
    }

    @Transactional
    public TournamentAdminPlayoffBracketsDTO saveSlots(Long tournamentAdminId, Long bracketId, SaveBracketSlotsRequest request) {
        TournamentAdminPlayoffBracket bracket = getBracketOrThrow(tournamentAdminId, bracketId);

        List<TournamentAdminPlayoffBracketSlot> round1Slots = slotRepository
                .findByBracketIdOrderByRoundNumberAscSlotIndexAsc(bracketId)
                .stream()
                .filter(s -> s.getRoundNumber() == 1)
                .collect(Collectors.toList());
        Map<Long, TournamentAdminPlayoffBracketSlot> round1ById = round1Slots.stream()
                .collect(Collectors.toMap(TournamentAdminPlayoffBracketSlot::getId, s -> s));

        Set<Long> qualifiedPlayerIds = playoffResultRepository
                .findByTournamentAdminIdAndScoreTypeOrderByPositionAsc(tournamentAdminId, bracket.getScoreType())
                .stream()
                .filter(TournamentAdminPlayoffResult::getQualified)
                .map(r -> r.getPlayer().getId())
                .collect(Collectors.toSet());

        Set<Long> seenSlotIds = new HashSet<>();
        Map<Long, Long> proposedPlayerBySlotId = new HashMap<>();
        for (TournamentAdminPlayoffBracketSlot slot : round1Slots) {
            proposedPlayerBySlotId.put(slot.getId(), slot.getPlayer() != null ? slot.getPlayer().getId() : null);
        }

        for (SaveBracketSlotsRequest.SlotAssignmentRequest assignment : request.getAssignments()) {
            if (!round1ById.containsKey(assignment.getSlotId())) {
                throw new BadRequestException("El casillero " + assignment.getSlotId() +
                        " no pertenece a la ronda 1 de esta llave");
            }
            if (!seenSlotIds.add(assignment.getSlotId())) {
                throw new BadRequestException("El casillero " + assignment.getSlotId() + " está repetido en la solicitud");
            }
            if (assignment.getPlayerId() != null && !qualifiedPlayerIds.contains(assignment.getPlayerId())) {
                throw new BadRequestException("El jugador seleccionado no está clasificado para esta llave");
            }
            proposedPlayerBySlotId.put(assignment.getSlotId(), assignment.getPlayerId());
        }

        List<Long> assignedPlayerIds = proposedPlayerBySlotId.values().stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
        Set<Long> uniquePlayerIds = new HashSet<>(assignedPlayerIds);
        if (uniquePlayerIds.size() != assignedPlayerIds.size()) {
            throw new BadRequestException("Un jugador no puede ocupar más de un casillero en la misma llave");
        }

        for (SaveBracketSlotsRequest.SlotAssignmentRequest assignment : request.getAssignments()) {
            TournamentAdminPlayoffBracketSlot slot = round1ById.get(assignment.getSlotId());
            Long currentPlayerId = slot.getPlayer() != null ? slot.getPlayer().getId() : null;
            boolean changed = !java.util.Objects.equals(currentPlayerId, assignment.getPlayerId());
            if (!changed) {
                continue;
            }
            if (Boolean.TRUE.equals(slot.getIsWinner())) {
                resetDescendantsFrom(bracketId, slot.getRoundNumber() + 1, slot.getSlotIndex() / 2);
                slot.setIsWinner(false);
            }
            slot.setPlayer(assignment.getPlayerId() != null ? playerRepository.getReferenceById(assignment.getPlayerId()) : null);
            slotRepository.save(slot);
        }

        return getBrackets(tournamentAdminId);
    }

    @Transactional
    public TournamentAdminPlayoffBracketsDTO confirm(Long tournamentAdminId, Long bracketId) {
        TournamentAdminPlayoffBracket bracket = getBracketOrThrow(tournamentAdminId, bracketId);

        TournamentAdminPlayoffBracketsDTO.BracketDTO dto = toBracketDTO(bracket);
        if (!dto.getUnassignedPlayers().isEmpty()) {
            throw new BadRequestException("Quedan " + dto.getUnassignedPlayers().size() +
                    " clasificado(s) sin ubicar en la llave. Ubicalos en un casillero antes de confirmar.");
        }

        bracket.setStatus("CONFIRMED");
        bracketRepository.save(bracket);
        return getBrackets(tournamentAdminId);
    }

    @Transactional
    public TournamentAdminPlayoffBracketsDTO revert(Long tournamentAdminId, Long bracketId) {
        TournamentAdminPlayoffBracket bracket = getBracketOrThrow(tournamentAdminId, bracketId);
        if (!"CONFIRMED".equals(bracket.getStatus())) {
            throw new BadRequestException("La llave no está confirmada");
        }
        if (slotRepository.existsByBracketIdAndIsWinnerTrue(bracketId)) {
            throw new BadRequestException(
                    "No se puede revertir la llave a edición: ya hay partidos jugados (Vencedores marcados)");
        }
        bracket.setStatus("DRAFT");
        bracketRepository.save(bracket);
        return getBrackets(tournamentAdminId);
    }

    @Transactional
    public TournamentAdminPlayoffBracketsDTO reset(Long tournamentAdminId, Long bracketId) {
        TournamentAdminPlayoffBracket bracket = getBracketOrThrow(tournamentAdminId, bracketId);
        slotRepository.deleteByBracketId(bracketId);
        bracketRepository.delete(bracket);
        return getBrackets(tournamentAdminId);
    }

    @Transactional
    public TournamentAdminPlayoffBracketsDTO markWinner(Long tournamentAdminId, Long bracketId, Long slotId) {
        TournamentAdminPlayoffBracket bracket = getBracketOrThrow(tournamentAdminId, bracketId);
        if (!"CONFIRMED".equals(bracket.getStatus())) {
            throw new BadRequestException("La llave debe estar confirmada para poder jugar partidos");
        }

        TournamentAdminPlayoffBracketSlot slot = getSlotOrThrow(bracketId, slotId);
        if (slot.getPlayer() == null) {
            throw new BadRequestException("El casillero no tiene un jugador asignado");
        }

        int siblingIndex = slot.getSlotIndex() % 2 == 0 ? slot.getSlotIndex() + 1 : slot.getSlotIndex() - 1;
        Optional<TournamentAdminPlayoffBracketSlot> siblingOpt = slotRepository
                .findByBracketIdAndRoundNumberAndSlotIndex(bracketId, slot.getRoundNumber(), siblingIndex);

        if (siblingOpt.isPresent() && Boolean.TRUE.equals(siblingOpt.get().getIsWinner())) {
            resetDescendantsFrom(bracketId, slot.getRoundNumber() + 1, slot.getSlotIndex() / 2);
            TournamentAdminPlayoffBracketSlot sibling = siblingOpt.get();
            sibling.setIsWinner(false);
            slotRepository.save(sibling);
        }

        slot.setIsWinner(true);
        slotRepository.save(slot);

        propagateWinner(bracketId, slot);

        return getBrackets(tournamentAdminId);
    }

    @Transactional
    public TournamentAdminPlayoffBracketsDTO undoWinner(Long tournamentAdminId, Long bracketId, Long slotId) {
        TournamentAdminPlayoffBracket bracket = getBracketOrThrow(tournamentAdminId, bracketId);
        if (!"CONFIRMED".equals(bracket.getStatus())) {
            throw new BadRequestException("La llave debe estar confirmada para poder editar partidos");
        }

        TournamentAdminPlayoffBracketSlot slot = getSlotOrThrow(bracketId, slotId);
        if (!Boolean.TRUE.equals(slot.getIsWinner())) {
            throw new BadRequestException("Este casillero no tiene un Vencedor marcado");
        }

        resetDescendantsFrom(bracketId, slot.getRoundNumber() + 1, slot.getSlotIndex() / 2);
        slot.setIsWinner(false);
        slotRepository.save(slot);

        return getBrackets(tournamentAdminId);
    }

    @Transactional(readOnly = true)
    public TournamentAdminPlayoffBracketsDTO getPublicBrackets(Long tournamentAdminId) {
        return getBrackets(tournamentAdminId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void propagateWinner(Long bracketId, TournamentAdminPlayoffBracketSlot slot) {
        int nextRound = slot.getRoundNumber() + 1;
        int nextIndex = slot.getSlotIndex() / 2;
        Optional<TournamentAdminPlayoffBracketSlot> nextSlotOpt = slotRepository
                .findByBracketIdAndRoundNumberAndSlotIndex(bracketId, nextRound, nextIndex);
        if (nextSlotOpt.isEmpty()) {
            return; // slot era de la ronda final, no hay propagación
        }
        TournamentAdminPlayoffBracketSlot nextSlot = nextSlotOpt.get();
        if (Boolean.TRUE.equals(nextSlot.getIsWinner())) {
            resetDescendantsFrom(bracketId, nextRound + 1, nextIndex / 2);
        }
        nextSlot.setPlayer(slot.getPlayer());
        nextSlot.setIsWinner(false);
        slotRepository.save(nextSlot);
    }

    private void resetDescendantsFrom(Long bracketId, int round, int slotIndex) {
        int r = round;
        int idx = slotIndex;
        while (true) {
            Optional<TournamentAdminPlayoffBracketSlot> slotOpt = slotRepository
                    .findByBracketIdAndRoundNumberAndSlotIndex(bracketId, r, idx);
            if (slotOpt.isEmpty()) {
                break;
            }
            TournamentAdminPlayoffBracketSlot slot = slotOpt.get();
            boolean hadData = slot.getPlayer() != null || Boolean.TRUE.equals(slot.getIsWinner());
            if (!hadData) {
                break;
            }
            slot.setPlayer(null);
            slot.setIsWinner(false);
            slotRepository.save(slot);
            r = r + 1;
            idx = idx / 2;
        }
    }

    private TournamentAdminPlayoffBracketsDTO.BracketDTO toBracketDTO(TournamentAdminPlayoffBracket bracket) {
        List<TournamentAdminPlayoffBracketSlot> slots = slotRepository
                .findByBracketIdOrderByRoundNumberAscSlotIndexAsc(bracket.getId());

        List<TournamentAdminPlayoffResult> qualifiedResults = playoffResultRepository
                .findByTournamentAdminIdAndScoreTypeOrderByPositionAsc(bracket.getTournamentAdmin().getId(), bracket.getScoreType())
                .stream()
                .filter(TournamentAdminPlayoffResult::getQualified)
                .collect(Collectors.toList());
        Map<Long, Integer> seedByPlayerId = qualifiedResults.stream()
                .collect(Collectors.toMap(r -> r.getPlayer().getId(), TournamentAdminPlayoffResult::getPosition));

        Map<Integer, List<TournamentAdminPlayoffBracketSlot>> slotsByRound = slots.stream()
                .collect(Collectors.groupingBy(TournamentAdminPlayoffBracketSlot::getRoundNumber, java.util.TreeMap::new, Collectors.toList()));

        int totalRounds = log2(bracket.getSize());
        List<TournamentAdminPlayoffBracketsDTO.RoundDTO> roundDTOs = new ArrayList<>();
        for (Map.Entry<Integer, List<TournamentAdminPlayoffBracketSlot>> entry : slotsByRound.entrySet()) {
            List<TournamentAdminPlayoffBracketsDTO.SlotDTO> slotDTOs = entry.getValue().stream()
                    .sorted(Comparator.comparing(TournamentAdminPlayoffBracketSlot::getSlotIndex))
                    .map(s -> toSlotDTO(s, seedByPlayerId))
                    .collect(Collectors.toList());
            roundDTOs.add(TournamentAdminPlayoffBracketsDTO.RoundDTO.builder()
                    .roundNumber(entry.getKey())
                    .roundName(roundName(bracket.getSize(), entry.getKey(), totalRounds))
                    .slots(slotDTOs)
                    .build());
        }

        Set<Long> assignedPlayerIds = slots.stream()
                .filter(s -> s.getRoundNumber() == 1 && s.getPlayer() != null)
                .map(s -> s.getPlayer().getId())
                .collect(Collectors.toSet());

        List<TournamentAdminPlayoffBracketsDTO.PlayerRefDTO> unassigned = qualifiedResults.stream()
                .filter(r -> !assignedPlayerIds.contains(r.getPlayer().getId()))
                .sorted(Comparator.comparing(TournamentAdminPlayoffResult::getPosition))
                .map(r -> TournamentAdminPlayoffBracketsDTO.PlayerRefDTO.builder()
                        .playerId(r.getPlayer().getId())
                        .playerName(r.getPlayer().getApellido() + " " + r.getPlayer().getNombre())
                        .playerHandicapIndex(toDouble(r.getPlayer().getHandicapIndex()))
                        .seed(r.getPosition())
                        .build())
                .collect(Collectors.toList());

        boolean canRevertToDraft = "CONFIRMED".equals(bracket.getStatus())
                && !slotRepository.existsByBracketIdAndIsWinnerTrue(bracket.getId());

        return TournamentAdminPlayoffBracketsDTO.BracketDTO.builder()
                .bracketId(bracket.getId())
                .scoreType(bracket.getScoreType())
                .size(bracket.getSize())
                .status(bracket.getStatus())
                .canRevertToDraft(canRevertToDraft)
                .rounds(roundDTOs)
                .unassignedPlayers(unassigned)
                .build();
    }

    private TournamentAdminPlayoffBracketsDTO.SlotDTO toSlotDTO(
            TournamentAdminPlayoffBracketSlot slot, Map<Long, Integer> seedByPlayerId) {
        Player player = slot.getPlayer();
        Long playerId = player != null ? player.getId() : null;
        return TournamentAdminPlayoffBracketsDTO.SlotDTO.builder()
                .slotId(slot.getId())
                .slotIndex(slot.getSlotIndex())
                .playerId(playerId)
                .playerName(player != null ? player.getApellido() + " " + player.getNombre() : null)
                .playerHandicapIndex(player != null ? toDouble(player.getHandicapIndex()) : null)
                .playerSeed(playerId != null ? seedByPlayerId.get(playerId) : null)
                .isWinner(slot.getIsWinner())
                .build();
    }

    private String roundName(int bracketSize, int roundNumber, int totalRounds) {
        int slotsInRound = bracketSize / (int) Math.pow(2, roundNumber - 1);
        switch (slotsInRound) {
            case 2:
                return "Final";
            case 4:
                return "Semifinal";
            case 8:
                return "Cuartos de Final";
            case 16:
                return "Octavos de Final";
            case 32:
                return "Dieciseisavos de Final";
            default:
                return "Ronda " + roundNumber + " de " + totalRounds;
        }
    }

    private Double toDouble(java.math.BigDecimal value) {
        return value != null ? value.doubleValue() : null;
    }

    private boolean isScratchApplicable(TournamentAdmin admin) {
        if (!"CLASICO".equals(admin.getTipo())) {
            return false;
        }
        ScoringConfigDTO config = scoringConfigService.getOrDefaultByTournamentAdminId(admin.getId());
        Integer scratchPositions = config.getQualifiedPlayoffPositionsScratch();
        return scratchPositions != null && scratchPositions > 0;
    }

    private int nextPowerOfTwo(int n) {
        int size = 2;
        while (size < n) {
            size *= 2;
        }
        return size;
    }

    private int log2(int size) {
        int rounds = 0;
        int s = size;
        while (s > 1) {
            s /= 2;
            rounds++;
        }
        return rounds;
    }

    private TournamentAdmin getAdminOrThrow(Long tournamentAdminId) {
        return tournamentAdminRepository.findById(tournamentAdminId)
                .orElseThrow(() -> new ResourceNotFoundException("TournamentAdmin", "id", tournamentAdminId));
    }

    private TournamentAdminPlayoffBracket getBracketOrThrow(Long tournamentAdminId, Long bracketId) {
        TournamentAdminPlayoffBracket bracket = bracketRepository.findById(bracketId)
                .orElseThrow(() -> new ResourceNotFoundException("TournamentAdminPlayoffBracket", "id", bracketId));
        if (!bracket.getTournamentAdmin().getId().equals(tournamentAdminId)) {
            throw new ResourceNotFoundException("TournamentAdminPlayoffBracket", "id", bracketId);
        }
        return bracket;
    }

    private TournamentAdminPlayoffBracketSlot getSlotOrThrow(Long bracketId, Long slotId) {
        TournamentAdminPlayoffBracketSlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new ResourceNotFoundException("TournamentAdminPlayoffBracketSlot", "id", slotId));
        if (!slot.getBracket().getId().equals(bracketId)) {
            throw new ResourceNotFoundException("TournamentAdminPlayoffBracketSlot", "id", slotId);
        }
        return slot;
    }
}
