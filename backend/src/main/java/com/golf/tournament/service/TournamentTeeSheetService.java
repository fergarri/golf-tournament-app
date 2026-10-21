package com.golf.tournament.service;

import com.golf.tournament.dto.teesheet.PublicTeeSheetDTO;
import com.golf.tournament.dto.teesheet.SaveTeeSheetRequest;
import com.golf.tournament.dto.teesheet.TournamentTeeSheetDTO;
import com.golf.tournament.exception.BadRequestException;
import com.golf.tournament.exception.ResourceNotFoundException;
import com.golf.tournament.model.Player;
import com.golf.tournament.model.Tournament;
import com.golf.tournament.model.TournamentInscription;
import com.golf.tournament.model.TournamentTeeSheet;
import com.golf.tournament.model.TournamentTeeSheetGroup;
import com.golf.tournament.repository.TournamentInscriptionRepository;
import com.golf.tournament.repository.TournamentRepository;
import com.golf.tournament.repository.TournamentTeeSheetGroupRepository;
import com.golf.tournament.repository.TournamentTeeSheetRepository;
import com.golf.tournament.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.text.Collator;

@Service
@RequiredArgsConstructor
public class TournamentTeeSheetService {

    private static final int MAX_GROUPS = 200;
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final Pattern TEE_TIME = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    private final TournamentRepository tournamentRepository;
    private final TournamentInscriptionRepository inscriptionRepository;
    private final TournamentTeeSheetRepository teeSheetRepository;
    private final TournamentTeeSheetGroupRepository groupRepository;
    private final CurrentUserProvider currentUserProvider;

    @Transactional
    public TournamentTeeSheetDTO getOrCreate(Long tournamentId) {
        Tournament tournament = loadTournament(tournamentId);
        currentUserProvider.assertClubAccess(tournament.getCourse().getId());
        TournamentTeeSheet sheet = findOrCreate(tournament);
        List<TournamentInscription> inscriptions = inscriptionRepository.findByTournamentIdWithPlayer(tournamentId);
        List<TournamentTeeSheetGroup> draft = reconcileDraft(sheet, inscriptions);
        boolean published = groupRepository.existsByTeeSheetIdAndPublished(sheet.getId(), true);
        return toEditorDto(tournament, sheet, draft, inscriptions, published);
    }

    @Transactional
    public TournamentTeeSheetDTO save(Long tournamentId, SaveTeeSheetRequest request) {
        Tournament tournament = loadTournament(tournamentId);
        currentUserProvider.assertClubAccess(tournament.getCourse().getId());
        TournamentTeeSheet sheet = findOrCreate(tournament);
        if (Boolean.TRUE.equals(sheet.getLocked())) {
            throw new BadRequestException("Las salidas están confirmadas. Tocá Editar Salidas para modificarlas.");
        }
        List<TournamentInscription> inscriptions = inscriptionRepository.findByTournamentIdWithPlayer(tournamentId);
        List<TournamentTeeSheetGroup> draft = replaceDraft(
                sheet, request.getGroups(), inscriptions, holesInPlay(tournament));
        boolean published = groupRepository.existsByTeeSheetIdAndPublished(sheet.getId(), true);
        return toEditorDto(tournament, sheet, draft, inscriptions, published);
    }

    @Transactional
    public TournamentTeeSheetDTO confirm(Long tournamentId) {
        Tournament tournament = loadTournament(tournamentId);
        currentUserProvider.assertClubAccess(tournament.getCourse().getId());
        TournamentTeeSheet sheet = teeSheetRepository.findByTournamentId(tournamentId)
                .orElseThrow(() -> new BadRequestException("No hay una grilla de salidas para confirmar."));
        if (Boolean.TRUE.equals(sheet.getLocked())) {
            throw new BadRequestException("Las salidas ya están confirmadas.");
        }
        List<TournamentInscription> inscriptions = inscriptionRepository.findByTournamentIdWithPlayer(tournamentId);
        List<TournamentTeeSheetGroup> draft = groupRepository.findFetchedByTeeSheetIdAndPublished(sheet.getId(), false);
        assertReadyToConfirm(draft, inscriptions);
        boolean packed = false;
        for (TournamentTeeSheetGroup group : draft) {
            if (packPlayersLeft(group)) {
                packed = true;
            }
        }
        if (packed) {
            groupRepository.saveAll(draft);
        }

        List<TournamentTeeSheetGroup> publishedExisting =
                groupRepository.findFetchedByTeeSheetIdAndPublished(sheet.getId(), true);
        if (!publishedExisting.isEmpty()) {
            groupRepository.deleteAll(publishedExisting);
            groupRepository.flush();
        }
        List<TournamentTeeSheetGroup> publishedGroups = new ArrayList<>();
        for (int i = 0; i < draft.size(); i++) {
            publishedGroups.add(copyAsPublished(sheet, draft.get(i), i));
        }
        groupRepository.saveAll(publishedGroups);
        sheet.setLocked(true);
        teeSheetRepository.save(sheet);
        return toEditorDto(tournament, sheet, draft, inscriptions, true);
    }

    @Transactional
    public TournamentTeeSheetDTO unlock(Long tournamentId) {
        Tournament tournament = loadTournament(tournamentId);
        currentUserProvider.assertClubAccess(tournament.getCourse().getId());
        TournamentTeeSheet sheet = teeSheetRepository.findByTournamentId(tournamentId)
                .orElseThrow(() -> new BadRequestException("No hay una grilla de salidas para editar."));
        if (Boolean.TRUE.equals(sheet.getLocked())) {
            sheet.setLocked(false);
            teeSheetRepository.save(sheet);
        }
        List<TournamentInscription> inscriptions = inscriptionRepository.findByTournamentIdWithPlayer(tournamentId);
        List<TournamentTeeSheetGroup> draft = reconcileDraft(sheet, inscriptions);
        boolean published = groupRepository.existsByTeeSheetIdAndPublished(sheet.getId(), true);
        return toEditorDto(tournament, sheet, draft, inscriptions, published);
    }

    @Transactional(readOnly = true)
    public PublicTeeSheetDTO getPublic(String codigo) {
        Tournament tournament = tournamentRepository.findByCodigo(codigo)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", "codigo", codigo));
        TournamentTeeSheet sheet = teeSheetRepository.findByTournamentId(tournament.getId()).orElse(null);
        boolean published = sheet != null && groupRepository.existsByTeeSheetIdAndPublished(sheet.getId(), true);
        List<PublicTeeSheetDTO.GroupDTO> groups = List.of();
        if (published) {
            groups = groupRepository.findFetchedByTeeSheetIdAndPublished(sheet.getId(), true).stream()
                    .map(this::toPublicGroup)
                    .toList();
        }
        return PublicTeeSheetDTO.builder()
                .tournamentName(tournament.getNombre())
                .courseName(tournament.getCourse().getNombre())
                .fechaInicio(tournament.getFechaInicio())
                .published(published)
                .groups(groups)
                .build();
    }

    private Tournament loadTournament(Long tournamentId) {
        return tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", "id", tournamentId));
    }

    private TournamentTeeSheet findOrCreate(Tournament tournament) {
        return teeSheetRepository.findByTournamentId(tournament.getId())
                .orElseGet(() -> createSheet(tournament));
    }

    private TournamentTeeSheet createSheet(Tournament tournament) {
        try {
            TournamentTeeSheet sheet = teeSheetRepository.save(TournamentTeeSheet.builder()
                    .tournament(tournament)
                    .locked(false)
                    .build());
            groupRepository.save(emptyDraft(sheet, 0));
            return sheet;
        } catch (DataIntegrityViolationException ex) {
            return teeSheetRepository.findByTournamentId(tournament.getId())
                    .orElseThrow(() -> ex);
        }
    }

    /**
     * Quita del borrador a quien ya no está inscripto. La copia publicada no se toca.
     */
    private List<TournamentTeeSheetGroup> reconcileDraft(TournamentTeeSheet sheet, List<TournamentInscription> inscriptions) {
        Set<Long> inscribedIds = inscriptions.stream()
                .map(inscription -> inscription.getPlayer().getId())
                .collect(Collectors.toSet());
        List<TournamentTeeSheetGroup> draft = groupRepository.findFetchedByTeeSheetIdAndPublished(sheet.getId(), false);
        boolean changed = false;
        for (TournamentTeeSheetGroup group : draft) {
            if (clearIfMissing(group.getPlayer1(), inscribedIds)) {
                group.setPlayer1(null);
                changed = true;
            }
            if (clearIfMissing(group.getPlayer2(), inscribedIds)) {
                group.setPlayer2(null);
                changed = true;
            }
            if (clearIfMissing(group.getPlayer3(), inscribedIds)) {
                group.setPlayer3(null);
                changed = true;
            }
            if (clearIfMissing(group.getPlayer4(), inscribedIds)) {
                group.setPlayer4(null);
                changed = true;
            }
        }
        if (changed) {
            groupRepository.saveAll(draft);
        }
        return draft;
    }

    private boolean clearIfMissing(Player player, Set<Long> inscribedIds) {
        return player != null && !inscribedIds.contains(player.getId());
    }

    private List<TournamentTeeSheetGroup> replaceDraft(
            TournamentTeeSheet sheet,
            List<SaveTeeSheetRequest.GroupRequest> requests,
            List<TournamentInscription> inscriptions,
            int holesInPlay) {
        if (requests == null) {
            throw new BadRequestException("La grilla es obligatoria.");
        }
        if (requests.size() > MAX_GROUPS) {
            throw new BadRequestException("No se pueden guardar más de " + MAX_GROUPS + " filas.");
        }
        Map<Long, Player> playersById = inscriptions.stream()
                .map(TournamentInscription::getPlayer)
                .collect(Collectors.toMap(Player::getId, Function.identity(), (left, right) -> left));
        Set<Long> seen = new HashSet<>();
        List<TournamentTeeSheetGroup> groups = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            SaveTeeSheetRequest.GroupRequest request = requests.get(i);
            TournamentTeeSheetGroup group = TournamentTeeSheetGroup.builder()
                    .teeSheet(sheet)
                    .sortOrder(i)
                    .published(false)
                    .holeNumber(parseHole(request.getHoleNumber(), holesInPlay))
                    .teeTime(parseTeeTime(request.getTeeTime()))
                    .player1(resolvePlayer(request.getPlayer1Id(), playersById, seen))
                    .player2(resolvePlayer(request.getPlayer2Id(), playersById, seen))
                    .player3(resolvePlayer(request.getPlayer3Id(), playersById, seen))
                    .player4(resolvePlayer(request.getPlayer4Id(), playersById, seen))
                    .build();
            groups.add(group);
        }
        List<TournamentTeeSheetGroup> existing = groupRepository.findFetchedByTeeSheetIdAndPublished(sheet.getId(), false);
        if (!existing.isEmpty()) {
            groupRepository.deleteAll(existing);
            groupRepository.flush();
        }
        return groupRepository.saveAll(groups);
    }

    private void assertReadyToConfirm(List<TournamentTeeSheetGroup> draft, List<TournamentInscription> inscriptions) {
        Set<Long> inscribedIds = inscriptions.stream()
                .map(inscription -> inscription.getPlayer().getId())
                .collect(Collectors.toSet());
        if (inscribedIds.isEmpty()) {
            throw new BadRequestException("No hay jugadores inscriptos para confirmar las salidas.");
        }
        Set<Long> placed = new HashSet<>();
        for (TournamentTeeSheetGroup group : draft) {
            collect(placed, group.getPlayer1());
            collect(placed, group.getPlayer2());
            collect(placed, group.getPlayer3());
            collect(placed, group.getPlayer4());
        }
        if (!inscribedIds.containsAll(placed)) {
            throw new BadRequestException("La lista de inscriptos cambió. Actualizá la página antes de confirmar.");
        }
        if (!placed.containsAll(inscribedIds)) {
            throw new BadRequestException("Ubicá a todos los jugadores antes de confirmar.");
        }
    }

    private void collect(Set<Long> placed, Player player) {
        if (player != null) {
            placed.add(player.getId());
        }
    }

    private Integer parseHole(Integer holeNumber, int holesInPlay) {
        if (holeNumber == null) {
            return null;
        }
        if (holeNumber < 1 || holeNumber > holesInPlay) {
            throw new BadRequestException("El hoyo debe estar entre 1 y " + holesInPlay + ".");
        }
        return holeNumber;
    }

    private LocalTime parseTeeTime(String teeTime) {
        if (teeTime == null || teeTime.isBlank()) {
            return null;
        }
        String value = teeTime.length() >= 5 ? teeTime.substring(0, 5) : teeTime;
        if (!TEE_TIME.matcher(value).matches()) {
            throw new BadRequestException("El horario debe tener formato HH:mm.");
        }
        return LocalTime.parse(value, HH_MM);
    }

    private Player resolvePlayer(Long playerId, Map<Long, Player> playersById, Set<Long> seen) {
        if (playerId == null) {
            return null;
        }
        Player player = playersById.get(playerId);
        if (player == null) {
            throw new BadRequestException("El jugador no está inscripto en el torneo.");
        }
        if (!seen.add(playerId)) {
            throw new BadRequestException("Un jugador no puede estar en más de una salida.");
        }
        return player;
    }

    private TournamentTeeSheetGroup emptyDraft(TournamentTeeSheet sheet, int sortOrder) {
        return TournamentTeeSheetGroup.builder()
                .teeSheet(sheet)
                .sortOrder(sortOrder)
                .published(false)
                .build();
    }

    /**
     * Corre los jugadores de la fila hacia la izquierda. La vista pública queda con los huecos a la derecha.
     */
    private boolean packPlayersLeft(TournamentTeeSheetGroup group) {
        Player[] current = { group.getPlayer1(), group.getPlayer2(), group.getPlayer3(), group.getPlayer4() };
        Player[] packed = new Player[4];
        int index = 0;
        for (Player player : current) {
            if (player != null) {
                packed[index++] = player;
            }
        }
        boolean changed = false;
        for (int i = 0; i < current.length; i++) {
            if (current[i] != packed[i]) {
                changed = true;
                break;
            }
        }
        if (!changed) {
            return false;
        }
        group.setPlayer1(packed[0]);
        group.setPlayer2(packed[1]);
        group.setPlayer3(packed[2]);
        group.setPlayer4(packed[3]);
        return true;
    }

    private TournamentTeeSheetGroup copyAsPublished(TournamentTeeSheet sheet, TournamentTeeSheetGroup draft, int sortOrder) {
        return TournamentTeeSheetGroup.builder()
                .teeSheet(sheet)
                .sortOrder(sortOrder)
                .published(true)
                .holeNumber(draft.getHoleNumber())
                .teeTime(draft.getTeeTime())
                .player1(draft.getPlayer1())
                .player2(draft.getPlayer2())
                .player3(draft.getPlayer3())
                .player4(draft.getPlayer4())
                .build();
    }

    private int holesInPlay(Tournament tournament) {
        Integer cantidad = tournament.getCantidadHoyosJuego();
        return cantidad != null && cantidad == 9 ? 9 : 18;
    }

    private TournamentTeeSheetDTO toEditorDto(
            Tournament tournament,
            TournamentTeeSheet sheet,
            List<TournamentTeeSheetGroup> draft,
            List<TournamentInscription> inscriptions,
            boolean published) {
        Collator collator = Collator.getInstance(new Locale("es"));
        collator.setStrength(Collator.PRIMARY);
        List<TournamentTeeSheetDTO.PlayerDTO> players = inscriptions.stream()
                .map(inscription -> {
                    Player player = inscription.getPlayer();
                    return TournamentTeeSheetDTO.PlayerDTO.builder()
                            .playerId(player.getId())
                            .playerName(player.getApellido() + " " + player.getNombre())
                            .nombre(player.getNombre())
                            .apellido(player.getApellido())
                            .handicapIndex(player.getHandicapIndex())
                            .build();
                })
                .sorted(Comparator
                        .comparing(TournamentTeeSheetDTO.PlayerDTO::getApellido, collator)
                        .thenComparing(TournamentTeeSheetDTO.PlayerDTO::getNombre, collator))
                .toList();
        List<TournamentTeeSheetDTO.GroupDTO> groups = draft.stream().map(this::toEditorGroup).toList();
        return TournamentTeeSheetDTO.builder()
                .tournamentId(tournament.getId())
                .tournamentName(tournament.getNombre())
                .codigo(tournament.getCodigo())
                .courseName(tournament.getCourse().getNombre())
                .fechaInicio(tournament.getFechaInicio())
                .cantidadHoyosJuego(holesInPlay(tournament))
                .locked(Boolean.TRUE.equals(sheet.getLocked()))
                .published(published)
                .groups(groups)
                .players(players)
                .build();
    }

    private TournamentTeeSheetDTO.GroupDTO toEditorGroup(TournamentTeeSheetGroup group) {
        return TournamentTeeSheetDTO.GroupDTO.builder()
                .holeNumber(group.getHoleNumber())
                .teeTime(group.getTeeTime() == null ? null : group.getTeeTime().format(HH_MM))
                .player1Id(playerId(group.getPlayer1()))
                .player2Id(playerId(group.getPlayer2()))
                .player3Id(playerId(group.getPlayer3()))
                .player4Id(playerId(group.getPlayer4()))
                .build();
    }

    private PublicTeeSheetDTO.GroupDTO toPublicGroup(TournamentTeeSheetGroup group) {
        return PublicTeeSheetDTO.GroupDTO.builder()
                .holeNumber(group.getHoleNumber())
                .teeTime(group.getTeeTime() == null ? null : group.getTeeTime().format(HH_MM))
                .player1Name(playerName(group.getPlayer1()))
                .player2Name(playerName(group.getPlayer2()))
                .player3Name(playerName(group.getPlayer3()))
                .player4Name(playerName(group.getPlayer4()))
                .build();
    }

    private Long playerId(Player player) {
        return player == null ? null : player.getId();
    }

    private String playerName(Player player) {
        return player == null ? null : player.getApellido() + " " + player.getNombre();
    }
}
