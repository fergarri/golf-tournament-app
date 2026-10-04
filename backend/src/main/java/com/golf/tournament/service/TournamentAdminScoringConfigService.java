package com.golf.tournament.service;

import com.golf.tournament.dto.tournamentadmin.SaveScoringConfigRequest;
import com.golf.tournament.dto.tournamentadmin.ScoringConfigDTO;
import com.golf.tournament.exception.BadRequestException;
import com.golf.tournament.exception.ResourceNotFoundException;
import com.golf.tournament.model.TournamentAdmin;
import com.golf.tournament.model.TournamentAdminScoringConfig;
import com.golf.tournament.model.TournamentAdminScoringPositionPoints;
import com.golf.tournament.repository.TournamentAdminRepository;
import com.golf.tournament.repository.TournamentAdminScoringConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TournamentAdminScoringConfigService {

    private static final int DEFAULT_BIRDIE_POINTS = 1;
    private static final int DEFAULT_EAGLE_POINTS = 5;
    private static final int DEFAULT_ACE_POINTS = 10;
    private static final int DEFAULT_PARTICIPATION_POINTS = 1;
    private static final int DEFAULT_REMAINING_POSITIONS_POINTS = 0;
    private static final int DEFAULT_QUALIFIED_PLAYOFF_POSITIONS = 8;
    private static final int DEFAULT_QUALIFIED_PLAYOFF_POSITIONS_SCRATCH = 0;
    private static final String DEFAULT_HCP_QUALIFIED_MODE = "GLOBAL";
    private static final String DEFAULT_TIE_BREAK_MODE = "NETO_HCP_HOLE";
    private static final boolean DEFAULT_DISCOUNT_UNDER_PAR = false;
    private static final BigDecimal DEFAULT_MATCH_PLAY_HCP_PERCENT = new BigDecimal("100.0");
    static final String UNDER_PAR_MODE_FIRST_PLACE = "FIRST_PLACE";

    private final TournamentAdminRepository tournamentAdminRepository;
    private final TournamentAdminScoringConfigRepository scoringConfigRepository;

    @Transactional(readOnly = true)
    public Optional<ScoringConfigDTO> getByTournamentAdminId(Long tournamentAdminId) {
        return scoringConfigRepository.findByTournamentAdminId(tournamentAdminId)
                .map(this::convertToDTO);
    }

    @Transactional(readOnly = true)
    public ScoringConfigDTO getOrDefaultByTournamentAdminId(Long tournamentAdminId) {
        return scoringConfigRepository.findByTournamentAdminId(tournamentAdminId)
                .map(this::convertToDTO)
                .orElseGet(() -> buildDefaultDTO(tournamentAdminId));
    }

    @Transactional
    public ScoringConfigDTO save(Long tournamentAdminId, SaveScoringConfigRequest request) {
        TournamentAdmin admin = tournamentAdminRepository.findById(tournamentAdminId)
                .orElseThrow(() -> new ResourceNotFoundException("TournamentAdmin", "id", tournamentAdminId));

        TournamentAdminScoringConfig config = scoringConfigRepository
                .findByTournamentAdminId(tournamentAdminId)
                .orElseGet(() -> TournamentAdminScoringConfig.builder()
                        .tournamentAdmin(admin)
                        .build());

        config.setBirdiePoints(request.getBirdiePoints());
        config.setEaglePoints(request.getEaglePoints());
        config.setAcePoints(request.getAcePoints());
        config.setParticipationPoints(request.getParticipationPoints());
        config.setRemainingPositionsPoints(request.getRemainingPositionsPoints());
        config.setQualifiedPlayoffPositions(request.getQualifiedPlayoffPositions());
        config.setQualifiedPlayoffPositionsScratch(request.getQualifiedPlayoffPositionsScratch() != null ? request.getQualifiedPlayoffPositionsScratch() : DEFAULT_QUALIFIED_PLAYOFF_POSITIONS_SCRATCH);
        config.setHcpQualifiedMode(request.getHcpQualifiedMode() != null ? request.getHcpQualifiedMode() : DEFAULT_HCP_QUALIFIED_MODE);
        config.setTieBreakMode(request.getTieBreakMode());
        boolean discountUnderPar = Boolean.TRUE.equals(request.getDiscountUnderPar());
        config.setDiscountUnderPar(discountUnderPar);
        config.setUnderParDiscountMode(discountUnderPar
                ? normalizeUnderParMode(request.getUnderParDiscountMode())
                : null);
        config.setMatchPlayHcpPercent(normalizeMatchPlayPercent(request.getMatchPlayHcpPercent()));

        // Forzar el flush de los DELETEs antes de insertar las nuevas posiciones
        // para evitar conflicto de clave única con orphanRemoval en Hibernate
        config.getPositionPoints().clear();
        scoringConfigRepository.saveAndFlush(config);

        if (request.getPositionPoints() != null) {
            List<TournamentAdminScoringPositionPoints> newPositions = request.getPositionPoints().stream()
                    .map(pp -> TournamentAdminScoringPositionPoints.builder()
                            .scoringConfig(config)
                            .position(pp.getPosition())
                            .points(pp.getPoints())
                            .build())
                    .collect(Collectors.toList());
            config.getPositionPoints().addAll(newPositions);
        }

        TournamentAdminScoringConfig saved = scoringConfigRepository.save(config);
        log.info("Configuración de puntuación guardada para TournamentAdmin {}", tournamentAdminId);
        return convertToDTO(saved);
    }

    private ScoringConfigDTO convertToDTO(TournamentAdminScoringConfig config) {
        List<ScoringConfigDTO.PositionPointsDTO> positions = config.getPositionPoints().stream()
                .sorted(Comparator.comparingInt(TournamentAdminScoringPositionPoints::getPosition))
                .map(pp -> ScoringConfigDTO.PositionPointsDTO.builder()
                        .position(pp.getPosition())
                        .points(pp.getPoints())
                        .build())
                .collect(Collectors.toList());

        return ScoringConfigDTO.builder()
                .id(config.getId())
                .tournamentAdminId(config.getTournamentAdmin().getId())
                .birdiePoints(config.getBirdiePoints())
                .eaglePoints(config.getEaglePoints())
                .acePoints(config.getAcePoints())
                .participationPoints(config.getParticipationPoints())
                .remainingPositionsPoints(config.getRemainingPositionsPoints())
                .qualifiedPlayoffPositions(config.getQualifiedPlayoffPositions())
                .qualifiedPlayoffPositionsScratch(config.getQualifiedPlayoffPositionsScratch() != null ? config.getQualifiedPlayoffPositionsScratch() : DEFAULT_QUALIFIED_PLAYOFF_POSITIONS_SCRATCH)
                .hcpQualifiedMode(config.getHcpQualifiedMode() != null ? config.getHcpQualifiedMode() : DEFAULT_HCP_QUALIFIED_MODE)
                .tieBreakMode(config.getTieBreakMode())
                .discountUnderPar(Boolean.TRUE.equals(config.getDiscountUnderPar()))
                .underParDiscountMode(Boolean.TRUE.equals(config.getDiscountUnderPar())
                        ? normalizeUnderParMode(config.getUnderParDiscountMode())
                        : null)
                .matchPlayHcpPercent(config.getMatchPlayHcpPercent() != null
                        ? config.getMatchPlayHcpPercent()
                        : DEFAULT_MATCH_PLAY_HCP_PERCENT)
                .positionPoints(positions)
                .build();
    }

    private ScoringConfigDTO buildDefaultDTO(Long tournamentAdminId) {
        return ScoringConfigDTO.builder()
                .tournamentAdminId(tournamentAdminId)
                .birdiePoints(DEFAULT_BIRDIE_POINTS)
                .eaglePoints(DEFAULT_EAGLE_POINTS)
                .acePoints(DEFAULT_ACE_POINTS)
                .participationPoints(DEFAULT_PARTICIPATION_POINTS)
                .remainingPositionsPoints(DEFAULT_REMAINING_POSITIONS_POINTS)
                .qualifiedPlayoffPositions(DEFAULT_QUALIFIED_PLAYOFF_POSITIONS)
                .qualifiedPlayoffPositionsScratch(DEFAULT_QUALIFIED_PLAYOFF_POSITIONS_SCRATCH)
                .hcpQualifiedMode(DEFAULT_HCP_QUALIFIED_MODE)
                .tieBreakMode(DEFAULT_TIE_BREAK_MODE)
                .discountUnderPar(DEFAULT_DISCOUNT_UNDER_PAR)
                .underParDiscountMode(null)
                .matchPlayHcpPercent(DEFAULT_MATCH_PLAY_HCP_PERCENT)
                .positionPoints(List.of(
                        new ScoringConfigDTO.PositionPointsDTO(1, 12),
                        new ScoringConfigDTO.PositionPointsDTO(2, 10),
                        new ScoringConfigDTO.PositionPointsDTO(3, 8),
                        new ScoringConfigDTO.PositionPointsDTO(4, 6),
                        new ScoringConfigDTO.PositionPointsDTO(5, 4),
                        new ScoringConfigDTO.PositionPointsDTO(6, 2)
                ))
                .build();
    }

    private BigDecimal normalizeMatchPlayPercent(BigDecimal raw) {
        BigDecimal percent = raw == null ? DEFAULT_MATCH_PLAY_HCP_PERCENT : raw;
        BigDecimal scaled = percent.setScale(1, RoundingMode.HALF_UP);
        if (scaled.compareTo(BigDecimal.ZERO) < 0 || scaled.compareTo(DEFAULT_MATCH_PLAY_HCP_PERCENT) > 0) {
            throw new BadRequestException("El porcentaje de HCP Course del Match Play debe estar entre 0 y 100");
        }
        return scaled;
    }

    private String normalizeUnderParMode(String mode) {
        if ("ALL_UNDER_PAR".equals(mode)) {
            return mode;
        }
        return UNDER_PAR_MODE_FIRST_PLACE;
    }
}
