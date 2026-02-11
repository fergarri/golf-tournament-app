package com.golf.tournament.service;

import com.golf.tournament.dto.tournament.*;
import com.golf.tournament.exception.BadRequestException;
import com.golf.tournament.exception.ResourceNotFoundException;
import com.golf.tournament.model.*;
import com.golf.tournament.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TournamentService {

    private final TournamentRepository tournamentRepository;
    private final CourseRepository courseRepository;
    private final CourseTeeRepository courseTeeRepository;
    private final TournamentCategoryRepository tournamentCategoryRepository;
    private final TournamentInscriptionRepository tournamentInscriptionRepository;
    private final TournamentTeeConfigRepository tournamentTeeConfigRepository;

    private static final String CODIGO_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODIGO_LENGTH = 8;
    private static final SecureRandom random = new SecureRandom();

    @Transactional(readOnly = true)
    public List<TournamentDTO> getAllTournaments() {
        return tournamentRepository.findAllOrderByFechaInicioDesc().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TournamentDTO getTournamentById(Long id) {
        Tournament tournament = tournamentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", "id", id));
        return convertToDTO(tournament);
    }

    @Transactional(readOnly = true)
    public TournamentDTO getTournamentByCodigo(String codigo) {
        Tournament tournament = tournamentRepository.findByCodigo(codigo)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", "codigo", codigo));
        return convertToDTO(tournament);
    }

    @Transactional
    public TournamentDTO createTournament(CreateTournamentRequest request) {
        Course course = courseRepository.findById(request.getCourseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", request.getCourseId()));

        validateTeeConfiguration(request.getTeeConfig());

        String codigo = generateUniqueCodigo();

        Tournament tournament = Tournament.builder()
                .nombre(request.getNombre())
                .codigo(codigo)
                .tipo(request.getTipo())
                .modalidad(request.getModalidad())
                .course(course)
                .fechaInicio(request.getFechaInicio())
                .fechaFin(request.getFechaFin())
                .limiteInscriptos(request.getLimiteInscriptos())
                .valorInscripcion(request.getValorInscripcion())
                .build();

        tournament = tournamentRepository.save(tournament);

        saveTeeConfig(tournament, request.getTeeConfig());

        for (TournamentCategoryDTO categoryDTO : request.getCategories()) {
            TournamentCategory category = TournamentCategory.builder()
                    .tournament(tournament)
                    .nombre(categoryDTO.getNombre())
                    .handicapMin(categoryDTO.getHandicapMin())
                    .handicapMax(categoryDTO.getHandicapMax())
                    .build();
            tournamentCategoryRepository.save(category);
        }

        log.info("Tournament created with id: {} and code: {}", tournament.getId(), codigo);
        return convertToDTO(tournament);
    }

    @Transactional
    public TournamentDTO updateTournament(Long id, CreateTournamentRequest request) {
        Tournament tournament = tournamentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", "id", id));

        Course course = courseRepository.findById(request.getCourseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", request.getCourseId()));

        validateTeeConfiguration(request.getTeeConfig());

        tournament.setNombre(request.getNombre());
        tournament.setTipo(request.getTipo());
        tournament.setModalidad(request.getModalidad());
        tournament.setCourse(course);
        tournament.setFechaInicio(request.getFechaInicio());
        tournament.setFechaFin(request.getFechaFin());
        tournament.setLimiteInscriptos(request.getLimiteInscriptos());
        tournament.setValorInscripcion(request.getValorInscripcion());
        tournament = tournamentRepository.save(tournament);

        tournamentCategoryRepository.deleteAll(tournament.getCategories());
        tournament.getCategories().clear();

        for (TournamentCategoryDTO categoryDTO : request.getCategories()) {
            TournamentCategory category = TournamentCategory.builder()
                    .tournament(tournament)
                    .nombre(categoryDTO.getNombre())
                    .handicapMin(categoryDTO.getHandicapMin())
                    .handicapMax(categoryDTO.getHandicapMax())
                    .build();
            tournamentCategoryRepository.save(category);
        }

        updateTeeConfig(tournament, request.getTeeConfig());

        log.info("Tournament updated with id: {}", tournament.getId());
        return convertToDTO(tournament);
    }

    @Transactional
    public void deleteTournament(Long id) {
        if (!tournamentRepository.existsById(id)) {
            throw new ResourceNotFoundException("Tournament", "id", id);
        }
        tournamentRepository.deleteById(id);
        log.info("Tournament deleted with id: {}", id);
    }

    private void validateTeeConfiguration(TournamentTeeConfigDTO teeConfig) {
        CourseTee tee1 = courseTeeRepository.findById(teeConfig.getCourseTeeIdPrimeros9())
                .orElseThrow(() -> new ResourceNotFoundException("CourseTee", "id", teeConfig.getCourseTeeIdPrimeros9()));

        if (!tee1.getActive()) {
            throw new BadRequestException("Selected tee for first 9 holes is not active");
        }

        if (teeConfig.getCourseTeeIdSegundos9() != null) {
            CourseTee tee2 = courseTeeRepository.findById(teeConfig.getCourseTeeIdSegundos9())
                    .orElseThrow(() -> new ResourceNotFoundException("CourseTee", "id", teeConfig.getCourseTeeIdSegundos9()));

            if (!tee2.getActive()) {
                throw new BadRequestException("Selected tee for second 9 holes is not active");
            }

            if (tee1.getGrupo() != null && tee2.getGrupo() != null &&
                    !tee1.getGrupo().equals(tee2.getGrupo())) {
                throw new BadRequestException("Both tees must be from the same group");
            }
        }
    }

    private void saveTeeConfig(Tournament tournament, TournamentTeeConfigDTO configDTO) {
        CourseTee tee1 = courseTeeRepository.findById(configDTO.getCourseTeeIdPrimeros9())
                .orElseThrow(() -> new ResourceNotFoundException("CourseTee", "id", configDTO.getCourseTeeIdPrimeros9()));

        CourseTee tee2 = null;
        if (configDTO.getCourseTeeIdSegundos9() != null) {
            tee2 = courseTeeRepository.findById(configDTO.getCourseTeeIdSegundos9())
                    .orElseThrow(() -> new ResourceNotFoundException("CourseTee", "id", configDTO.getCourseTeeIdSegundos9()));
        }

        TournamentTeeConfig config = TournamentTeeConfig.builder()
                .tournament(tournament)
                .courseTeeIdPrimeros9(tee1)
                .courseTeeIdSegundos9(tee2)
                .build();

        tournamentTeeConfigRepository.save(config);
    }

    private void updateTeeConfig(Tournament tournament, TournamentTeeConfigDTO configDTO) {
        CourseTee tee1 = courseTeeRepository.findById(configDTO.getCourseTeeIdPrimeros9())
                .orElseThrow(() -> new ResourceNotFoundException("CourseTee", "id", configDTO.getCourseTeeIdPrimeros9()));

        CourseTee tee2 = null;
        if (configDTO.getCourseTeeIdSegundos9() != null) {
            tee2 = courseTeeRepository.findById(configDTO.getCourseTeeIdSegundos9())
                    .orElseThrow(() -> new ResourceNotFoundException("CourseTee", "id", configDTO.getCourseTeeIdSegundos9()));
        }

        TournamentTeeConfig existingConfig = tournamentTeeConfigRepository.findByTournamentId(tournament.getId())
                .orElse(null);

        if (existingConfig != null) {
            existingConfig.setCourseTeeIdPrimeros9(tee1);
            existingConfig.setCourseTeeIdSegundos9(tee2);
            tournamentTeeConfigRepository.save(existingConfig);
        } else {
            TournamentTeeConfig config = TournamentTeeConfig.builder()
                    .tournament(tournament)
                    .courseTeeIdPrimeros9(tee1)
                    .courseTeeIdSegundos9(tee2)
                    .build();
            tournamentTeeConfigRepository.save(config);
        }
    }

    private String generateUniqueCodigo() {
        String codigo;
        do {
            codigo = generateRandomCodigo();
        } while (tournamentRepository.existsByCodigo(codigo));
        return codigo;
    }

    private String generateRandomCodigo() {
        StringBuilder sb = new StringBuilder(CODIGO_LENGTH);
        for (int i = 0; i < CODIGO_LENGTH; i++) {
            int index = random.nextInt(CODIGO_CHARS.length());
            sb.append(CODIGO_CHARS.charAt(index));
        }
        return sb.toString();
    }

    private TournamentDTO convertToDTO(Tournament tournament) {
        Long inscriptos = tournamentInscriptionRepository.countByTournamentId(tournament.getId());

        List<TournamentCategoryDTO> categories = tournament.getCategories().stream()
                .map(this::convertCategoryToDTO)
                .collect(Collectors.toList());

        TournamentTeeConfigDTO teeConfigDTO = null;
        if (tournament.getTeeConfig() != null) {
            teeConfigDTO = TournamentTeeConfigDTO.builder()
                    .id(tournament.getTeeConfig().getId())
                    .courseTeeIdPrimeros9(tournament.getTeeConfig().getCourseTeeIdPrimeros9().getId())
                    .courseTeeIdSegundos9(tournament.getTeeConfig().getCourseTeeIdSegundos9() != null ?
                            tournament.getTeeConfig().getCourseTeeIdSegundos9().getId() : null)
                    .build();
        }

        return TournamentDTO.builder()
                .id(tournament.getId())
                .nombre(tournament.getNombre())
                .codigo(tournament.getCodigo())
                .tipo(tournament.getTipo())
                .modalidad(tournament.getModalidad())
                .estado(tournament.getEstado())
                .courseId(tournament.getCourse().getId())
                .courseName(tournament.getCourse().getNombre())
                .fechaInicio(tournament.getFechaInicio())
                .fechaFin(tournament.getFechaFin())
                .limiteInscriptos(tournament.getLimiteInscriptos())
                .valorInscripcion(tournament.getValorInscripcion())
                .currentInscriptos(inscriptos.intValue())
                .categories(categories)
                .teeConfig(teeConfigDTO)
                .build();
    }

    @Transactional
    public TournamentDTO startTournament(Long id) {
        Tournament tournament = tournamentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", "id", id));

        if (!"PENDING".equals(tournament.getEstado())) {
            throw new BadRequestException("Tournament can only be started from PENDING status");
        }

        tournament.setEstado("IN_PROGRESS");
        tournament = tournamentRepository.save(tournament);
        log.info("Tournament {} started and set to IN_PROGRESS", id);
        return convertToDTO(tournament);
    }

    @Transactional
    public TournamentDTO finalizeTournament(Long id) {
        Tournament tournament = tournamentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", "id", id));

        tournament.setEstado("FINALIZED");
        tournament = tournamentRepository.save(tournament);
        log.info("Tournament {} finalized", id);
        return convertToDTO(tournament);
    }

    private TournamentCategoryDTO convertCategoryToDTO(TournamentCategory category) {
        return TournamentCategoryDTO.builder()
                .id(category.getId())
                .nombre(category.getNombre())
                .handicapMin(category.getHandicapMin())
                .handicapMax(category.getHandicapMax())
                .build();
    }
}
