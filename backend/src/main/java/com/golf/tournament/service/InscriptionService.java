package com.golf.tournament.service;

import com.golf.tournament.dto.inscription.InscriptionRequest;
import com.golf.tournament.dto.inscription.InscriptionResponse;
import com.golf.tournament.dto.player.PlayerDTO;
import com.golf.tournament.exception.BadRequestException;
import com.golf.tournament.exception.ResourceNotFoundException;
import com.golf.tournament.model.*;
import com.golf.tournament.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InscriptionService {

    private final TournamentRepository tournamentRepository;
    private final PlayerRepository playerRepository;
    private final TournamentInscriptionRepository inscriptionRepository;
    private final TournamentCategoryRepository categoryRepository;

    @Transactional
    public InscriptionResponse inscribePlayer(String codigo, InscriptionRequest request) {
        Tournament tournament = tournamentRepository.findByCodigo(codigo)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", "codigo", codigo));

        if (tournament.getLimiteInscriptos() != null) {
            Long currentInscriptos = inscriptionRepository.countByTournamentId(tournament.getId());
            if (currentInscriptos >= tournament.getLimiteInscriptos()) {
                throw new BadRequestException("Tournament has reached maximum inscriptions");
            }
        }

        // Buscar el jugador en la base de datos por matrícula
        Player player = playerRepository.findByMatricula(request.getMatricula())
                .orElseThrow(() -> new BadRequestException("El player no se encuentra registrado en la app. Por favor comunicarse con secretaria."));

        // Verificar si el jugador ya está inscrito en este torneo
        if (inscriptionRepository.existsByTournamentIdAndPlayerId(tournament.getId(), player.getId())) {
            throw new BadRequestException("Jugador ya inscrito en este torneo");
        }

        final BigDecimal handicapIndex = player.getHandicapIndex();
        TournamentCategory category = categoryRepository
                .findCategoryForHandicap(tournament.getId(), handicapIndex)
                .orElseThrow(() -> new BadRequestException(
                        "No se encontró categoría para el handicap index: " + handicapIndex));

        TournamentInscription inscription = TournamentInscription.builder()
                .tournament(tournament)
                .player(player)
                .category(category)
                .build();

        inscription = inscriptionRepository.save(inscription);

        log.info("Jugador {} inscrito en torneo {}", player.getId(), tournament.getId());

        return InscriptionResponse.builder()
                .inscriptionId(inscription.getId())
                .player(convertPlayerToDTO(player))
                .categoryName(category.getNombre())
                .message("Jugador inscrito en torneo")
                .build();
    }

    @Transactional
    public InscriptionResponse inscribePlayerManual(Long tournamentId, Long playerId) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", "id", tournamentId));

        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> new ResourceNotFoundException("Player", "id", playerId));

        if (inscriptionRepository.existsByTournamentIdAndPlayerId(tournamentId, playerId)) {
            throw new BadRequestException("Jugador ya inscrito en este torneo");
        }

        if (tournament.getLimiteInscriptos() != null) {
            Long currentInscriptos = inscriptionRepository.countByTournamentId(tournamentId);
            if (currentInscriptos >= tournament.getLimiteInscriptos()) {
                throw new BadRequestException("Torneo ha alcanzado su capacidad máxima");
            }
        }

        TournamentCategory category = categoryRepository
                .findCategoryForHandicap(tournamentId, player.getHandicapIndex())
                .orElseThrow(() -> new BadRequestException(
                        "No se encontró categoría para el handicap index: " + player.getHandicapIndex()));

        TournamentInscription inscription = TournamentInscription.builder()
                .tournament(tournament)
                .player(player)
                .category(category)
                .build();

        inscription = inscriptionRepository.save(inscription);

        log.info("Jugador {} inscrito en torneo {} por admin", playerId, tournamentId);

        return InscriptionResponse.builder()
                .inscriptionId(inscription.getId())
                .player(convertPlayerToDTO(player))
                .categoryName(category.getNombre())
                .message("Jugador inscrito en torneo por admin")
                .build();
    }

    @Transactional(readOnly = true)
    public List<InscriptionResponse> getTournamentInscriptions(Long tournamentId) {
        return inscriptionRepository.findByTournamentId(tournamentId).stream()
                .map(this::convertInscriptionToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void updateHandicapCourse(Long inscriptionId, BigDecimal handicapCourse) {
        TournamentInscription inscription = inscriptionRepository.findById(inscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Inscription", "id", inscriptionId));

        inscription.setHandicapCourse(handicapCourse);
        inscriptionRepository.save(inscription);

        log.info("Handicap actualizado para inscripción {}: {}", inscriptionId, handicapCourse);
    }

    @Transactional
    public void removeInscription(Long inscriptionId) {
        if (!inscriptionRepository.existsById(inscriptionId)) {
            throw new ResourceNotFoundException("Inscription", "id", inscriptionId);
        }
        inscriptionRepository.deleteById(inscriptionId);
        log.info("Inscripción eliminada: {}", inscriptionId);
    }


    private PlayerDTO convertPlayerToDTO(Player player) {
        return PlayerDTO.builder()
                .id(player.getId())
                .nombre(player.getNombre())
                .apellido(player.getApellido())
                .email(player.getEmail())
                .matricula(player.getMatricula())
                .fechaNacimiento(player.getFechaNacimiento())
                .handicapIndex(player.getHandicapIndex())
                .telefono(player.getTelefono())
                .clubOrigen(player.getClubOrigen())
                .build();
    }

    private InscriptionResponse convertInscriptionToResponse(TournamentInscription inscription) {
        return InscriptionResponse.builder()
                .inscriptionId(inscription.getId())
                .player(convertPlayerToDTO(inscription.getPlayer()))
                .categoryName(inscription.getCategory() != null ? inscription.getCategory().getNombre() : null)
                .handicapCourse(inscription.getHandicapCourse())
                .build();
    }
}
