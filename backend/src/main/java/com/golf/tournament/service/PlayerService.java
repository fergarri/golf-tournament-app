package com.golf.tournament.service;

import com.golf.tournament.dto.player.CreatePlayerRequest;
import com.golf.tournament.dto.player.PlayerDTO;
import com.golf.tournament.exception.DuplicateResourceException;
import com.golf.tournament.exception.ResourceNotFoundException;
import com.golf.tournament.model.Player;
import com.golf.tournament.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerService {

    private final PlayerRepository playerRepository;

    @Transactional(readOnly = true)
    public List<PlayerDTO> getAllPlayers() {
        return playerRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PlayerDTO getPlayerById(Long id) {
        Player player = playerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Player", "id", id));
        return convertToDTO(player);
    }

    @Transactional(readOnly = true)
    public PlayerDTO getPlayerByMatricula(String matricula) {
        Player player = playerRepository.findByMatricula(matricula)
                .orElseThrow(() -> new ResourceNotFoundException("Player", "matricula", matricula));
        return convertToDTO(player);
    }

    @Transactional(readOnly = true)
    public List<PlayerDTO> searchPlayers(String search) {
        return playerRepository.searchPlayers(search).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public PlayerDTO createPlayer(CreatePlayerRequest request) {
        if (playerRepository.existsByMatricula(request.getMatricula())) {
            throw new DuplicateResourceException("Player", "matricula", request.getMatricula());
        }

        Player player = Player.builder()
                .nombre(request.getNombre())
                .apellido(request.getApellido())
                .email(request.getEmail())
                .matricula(request.getMatricula())
                .fechaNacimiento(request.getFechaNacimiento())
                .handicapIndex(request.getHandicapIndex())
                .telefono(request.getTelefono())
                .clubOrigen(request.getClubOrigen())
                .build();

        player = playerRepository.save(player);
        log.info("Player created with id: {}", player.getId());
        return convertToDTO(player);
    }

    @Transactional
    public PlayerDTO updatePlayer(Long id, CreatePlayerRequest request) {
        Player player = playerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Player", "id", id));

        if (!player.getMatricula().equals(request.getMatricula()) &&
                playerRepository.existsByMatricula(request.getMatricula())) {
            throw new DuplicateResourceException("Player", "matricula", request.getMatricula());
        }

        player.setNombre(request.getNombre());
        player.setApellido(request.getApellido());
        player.setEmail(request.getEmail());
        player.setMatricula(request.getMatricula());
        player.setFechaNacimiento(request.getFechaNacimiento());
        player.setHandicapIndex(request.getHandicapIndex());
        player.setTelefono(request.getTelefono());
        player.setClubOrigen(request.getClubOrigen());

        player = playerRepository.save(player);
        log.info("Player updated with id: {}", player.getId());
        return convertToDTO(player);
    }

    @Transactional
    public void deletePlayer(Long id) {
        if (!playerRepository.existsById(id)) {
            throw new ResourceNotFoundException("Player", "id", id);
        }
        playerRepository.deleteById(id);
        log.info("Player deleted with id: {}", id);
    }

    private PlayerDTO convertToDTO(Player player) {
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
}
