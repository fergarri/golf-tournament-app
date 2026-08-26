package com.golf.tournament.service;

import com.golf.tournament.dto.tournament.*;
import com.golf.tournament.exception.BadRequestException;
import com.golf.tournament.exception.ResourceNotFoundException;
import com.golf.tournament.model.*;
import com.golf.tournament.repository.*;
import com.golf.tournament.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final ScorecardRepository scorecardRepository;
    private final HoleScoreRepository holeScoreRepository;
    private final TournamentPrizeService tournamentPrizeService;
    private final TournamentAdminStageRepository tournamentAdminStageRepository;
    private final TournamentAdminScoringConfigService tournamentAdminScoringConfigService;
    private final CurrentUserProvider currentUserProvider;

    private static final String CODIGO_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODIGO_LENGTH = 8;
    private static final SecureRandom random = new SecureRandom();
    private static final String CATEGORY_SEX_MALE = "M";
    private static final String CATEGORY_SEX_FEMALE = "F";
    private static final String CATEGORY_SEX_MIXED = "X";

    @Transactional(readOnly = true)
    public List<TournamentDTO> getAllTournaments() {
        List<Tournament> tournaments = currentUserProvider.isSuperAdmin()
                ? tournamentRepository.findAllOrderByFechaInicioDesc()
                : tournamentRepository.findByCourseIdOrderByFechaInicioDesc(currentUserProvider.getCurrentCourseId());
        return tournaments.stream()
                .map(tournament -> {
                    TournamentDTO dto = convertToDTO(tournament);
                    enrichWithAdminStage(dto, false);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public TournamentDTO getTournamentById(Long id) {
        Tournament tournament = tournamentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", "id", id));
        currentUserProvider.assertClubAccess(tournament.getCourse().getId());
        TournamentDTO dto = convertToDTO(tournament);
        enrichWithAdminStage(dto, true);
        return dto;
    }

    @Transactional(readOnly = true)
    public TournamentDTO getTournamentByCodigo(String codigo) {
        Tournament tournament = tournamentRepository.findByCodigo(codigo)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", "codigo", codigo));
        TournamentDTO dto = convertToDTO(tournament);
        enrichWithAdminStage(dto, true);
        return dto;
    }

    @Transactional
    public TournamentDTO createTournament(CreateTournamentRequest request) {
        Long courseId = resolveCourseIdForRequest(request.getCourseId());
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", courseId));
        validateCantidadHoyosJuego(request.getCantidadHoyosJuego());
        validateCategorySexes(request.getCategories());
        validateHorarios(request.getHorarioInicio(), request.getHorarioCierre());
        CourseTee teeMasculino = resolveTournamentTee(course, request.getTeeMasculinoId());
        CourseTee teeFemenino = resolveTournamentTee(course, request.getTeeFemeninoId());

        String codigo = generateUniqueCodigo();

        Boolean doublePoints = "FRUTALES".equals(request.getTipo()) && Boolean.TRUE.equals(request.getDoublePoints());

        Tournament tournament = Tournament.builder()
                .nombre(request.getNombre())
                .codigo(codigo)
                .tipo(request.getTipo())
                .modalidad(request.getModalidad())
                .course(course)
                .cantidadHoyosJuego(request.getCantidadHoyosJuego())
                .teeMasculino(teeMasculino)
                .teeFemenino(teeFemenino)
                .fechaInicio(request.getFechaInicio())
                .fechaFin(request.getFechaFin())
                .horarioInicio(request.getHorarioInicio())
                .horarioCierre(request.getHorarioCierre())
                .limiteInscriptos(request.getLimiteInscriptos())
                .valorInscripcion(request.getValorInscripcion())
                .doublePoints(doublePoints)
                .controlCruzado(Boolean.TRUE.equals(request.getControlCruzado()))
                .build();

        tournament = tournamentRepository.save(tournament);

        for (TournamentCategoryDTO categoryDTO : request.getCategories()) {
            TournamentCategory category = TournamentCategory.builder()
                    .tournament(tournament)
                    .nombre(categoryDTO.getNombre())
                    .handicapMin(categoryDTO.getHandicapMin())
                    .handicapMax(categoryDTO.getHandicapMax())
                    .sexoCategoria(normalizeCategorySex(categoryDTO.getSexoCategoria()))
                    .build();
            tournamentCategoryRepository.save(category);
        }

        tournamentPrizeService.syncPrizesForTournament(tournament, request.getPrizes());

        log.info("Tournament created with id: {} and code: {}", tournament.getId(), codigo);
        return convertToDTO(tournament);
    }

    @Transactional
    public TournamentDTO updateTournament(Long id, CreateTournamentRequest request) {
        Tournament tournament = tournamentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", "id", id));
        currentUserProvider.assertClubAccess(tournament.getCourse().getId());

        Long courseId = resolveCourseIdForRequest(request.getCourseId());
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", courseId));
        validateCantidadHoyosJuego(request.getCantidadHoyosJuego());
        validateCategorySexes(request.getCategories());
        validateHorarios(request.getHorarioInicio(), request.getHorarioCierre());
        CourseTee teeMasculino = resolveTournamentTee(course, request.getTeeMasculinoId());
        CourseTee teeFemenino = resolveTournamentTee(course, request.getTeeFemeninoId());

        tournament.setNombre(request.getNombre());
        tournament.setTipo(request.getTipo());
        tournament.setModalidad(request.getModalidad());
        tournament.setCourse(course);
        tournament.setCantidadHoyosJuego(request.getCantidadHoyosJuego());
        tournament.setTeeMasculino(teeMasculino);
        tournament.setTeeFemenino(teeFemenino);
        tournament.setFechaInicio(request.getFechaInicio());
        tournament.setFechaFin(request.getFechaFin());
        tournament.setHorarioInicio(request.getHorarioInicio());
        tournament.setHorarioCierre(request.getHorarioCierre());
        tournament.setLimiteInscriptos(request.getLimiteInscriptos());
        tournament.setValorInscripcion(request.getValorInscripcion());
        tournament.setDoublePoints("FRUTALES".equals(request.getTipo()) && Boolean.TRUE.equals(request.getDoublePoints()));
        tournament.setControlCruzado(Boolean.TRUE.equals(request.getControlCruzado()));
        tournament = tournamentRepository.save(tournament);

        // Smart UPDATE/CREATE/DELETE of categories
        boolean categoriesChanged = updateTournamentCategories(tournament, request.getCategories());

        // If categories changed, reassign all inscriptions
        if (categoriesChanged) {
            reassignInscriptionCategories(tournament.getId());
        }

        tournamentPrizeService.syncPrizesForTournament(tournament, request.getPrizes());

        log.info("Tournament updated with id: {}, categories changed: {}", tournament.getId(), categoriesChanged);
        return convertToDTO(tournament);
    }

    /**
     * Smart update of tournament categories: UPDATE existing, CREATE new, DELETE removed.
     * Returns true if any category was modified, created, or deleted.
     */
    private boolean updateTournamentCategories(Tournament tournament, List<TournamentCategoryDTO> requestCategories) {
        boolean categoriesChanged = false;
        
        // Get existing categories and create a map by ID for quick lookup
        List<TournamentCategory> existingCategories = tournamentCategoryRepository.findByTournamentId(tournament.getId());
        java.util.Map<Long, TournamentCategory> existingCategoriesMap = existingCategories.stream()
                .collect(java.util.stream.Collectors.toMap(TournamentCategory::getId, cat -> cat));
        
        // Track which existing categories were processed
        java.util.Set<Long> processedCategoryIds = new java.util.HashSet<>();
        
        // Process requested categories: UPDATE existing or CREATE new
        for (TournamentCategoryDTO categoryDTO : requestCategories) {
            if (categoryDTO.getId() != null && existingCategoriesMap.containsKey(categoryDTO.getId())) {
                // UPDATE existing category
                TournamentCategory existingCategory = existingCategoriesMap.get(categoryDTO.getId());
                
                boolean fieldChanged = false;
                
                if (!existingCategory.getNombre().equals(categoryDTO.getNombre())) {
                    existingCategory.setNombre(categoryDTO.getNombre());
                    fieldChanged = true;
                }
                
                if (existingCategory.getHandicapMin().compareTo(categoryDTO.getHandicapMin()) != 0) {
                    existingCategory.setHandicapMin(categoryDTO.getHandicapMin());
                    fieldChanged = true;
                }
                
                if (existingCategory.getHandicapMax().compareTo(categoryDTO.getHandicapMax()) != 0) {
                    existingCategory.setHandicapMax(categoryDTO.getHandicapMax());
                    fieldChanged = true;
                }

                String requestedCategorySex = normalizeCategorySex(categoryDTO.getSexoCategoria());
                String currentCategorySex = normalizeCategorySex(existingCategory.getSexoCategoria());
                if (!currentCategorySex.equals(requestedCategorySex)) {
                    existingCategory.setSexoCategoria(requestedCategorySex);
                    fieldChanged = true;
                }
                
                if (fieldChanged) {
                    tournamentCategoryRepository.save(existingCategory);
                    categoriesChanged = true;
                    log.debug("Updated category {} for tournament {}", existingCategory.getId(), tournament.getId());
                }
                
                processedCategoryIds.add(categoryDTO.getId());
                
            } else {
                // CREATE new category (no ID or ID not found)
                TournamentCategory newCategory = TournamentCategory.builder()
                        .tournament(tournament)
                        .nombre(categoryDTO.getNombre())
                        .handicapMin(categoryDTO.getHandicapMin())
                        .handicapMax(categoryDTO.getHandicapMax())
                        .sexoCategoria(normalizeCategorySex(categoryDTO.getSexoCategoria()))
                        .build();
                tournamentCategoryRepository.save(newCategory);
                categoriesChanged = true;
                log.debug("Created new category for tournament {}", tournament.getId());
            }
        }
        
        // DELETE categories that were not in the request
        for (TournamentCategory existingCategory : existingCategories) {
            if (!processedCategoryIds.contains(existingCategory.getId())) {
                tournamentCategoryRepository.delete(existingCategory);
                categoriesChanged = true;
                log.debug("Deleted category {} from tournament {}", existingCategory.getId(), tournament.getId());
            }
        }
        
        return categoriesChanged;
    }

    @Transactional
    public void deleteTournament(Long id) {
        Tournament tournament = tournamentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", "id", id));
        currentUserProvider.assertClubAccess(tournament.getCourse().getId());
        currentUserProvider.assertCanDelete();
        tournamentRepository.deleteById(id);
        log.info("Tournament deleted with id: {}", id);
    }

    /**
     * Resuelve el course al que debe pertenecer el torneo: para un admin de club (rol USER)
     * siempre es su propio club, sin importar lo que llegue en el request; para un superadmin
     * se respeta el course elegido explícitamente.
     */
    private Long resolveCourseIdForRequest(Long requestedCourseId) {
        if (currentUserProvider.isSuperAdmin()) {
            if (requestedCourseId == null) {
                throw new BadRequestException("Debe seleccionar el campo de golf del torneo");
            }
            return requestedCourseId;
        }
        Long currentCourseId = currentUserProvider.getCurrentCourseId();
        if (currentCourseId == null) {
            throw new BadRequestException("El usuario no tiene un club asignado");
        }
        return currentCourseId;
    }

    /**
     * Finds the appropriate category for a given handicap course value.
     * Returns null if the handicap doesn't fall within any category range.
     */
    private TournamentCategory findCategoryForHandicap(java.math.BigDecimal handicapCourse,
                                                       String playerSex,
                                                       List<TournamentCategory> categories) {
        if (handicapCourse == null || categories == null || categories.isEmpty()) {
            return null;
        }

        String normalizedPlayerSex = normalizePlayerSex(playerSex);
        for (TournamentCategory category : categories) {
            if (!categoryAppliesToPlayerSex(category, normalizedPlayerSex)) {
                continue;
            }
            // Check if handicapCourse is within the category range (inclusive)
            if (handicapCourse.compareTo(category.getHandicapMin()) >= 0 &&
                handicapCourse.compareTo(category.getHandicapMax()) <= 0) {
                return category;
            }
        }

        // No category found for this handicap
        return null;
    }

    /**
     * Reassigns categories to all inscriptions in a tournament based on their scorecard's handicapCourse.
     * Only processes inscriptions that have a scorecard with a defined handicapCourse.
     * Inscriptions without scorecard or handicapCourse will have category = null.
     */
    private void reassignInscriptionCategories(Long tournamentId) {
        log.info("Starting category reassignment for tournament {}", tournamentId);
        
        // Get all inscriptions for this tournament
        List<TournamentInscription> inscriptions = tournamentInscriptionRepository.findByTournamentId(tournamentId);
        
        // Get all current categories for this tournament
        List<TournamentCategory> categories = tournamentCategoryRepository.findByTournamentId(tournamentId);
        
        int reassignedCount = 0;
        int withoutCategoryCount = 0;
        
        for (TournamentInscription inscription : inscriptions) {
            TournamentCategory newCategory = null;

            BigDecimal handicapIndex = inscription.getPlayer().getHandicapIndex();
            if (handicapIndex != null) {
                newCategory = findCategoryForHandicap(
                        handicapIndex,
                        inscription.getPlayer().getSexo(),
                        categories
                );

                if (newCategory != null) {
                    reassignedCount++;
                } else {
                    withoutCategoryCount++;
                }
            } else {
                withoutCategoryCount++;
            }

            // Update inscription category (may be null)
            inscription.setCategory(newCategory);
        }
        
        // Save all updated inscriptions
        tournamentInscriptionRepository.saveAll(inscriptions);
        
        log.info("Category reassignment completed for tournament {}: {} assigned, {} without category", 
                 tournamentId, reassignedCount, withoutCategoryCount);
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

        List<TournamentPrizeDTO> prizes = tournamentPrizeService.getPrizesForTournament(tournament.getId());

        return TournamentDTO.builder()
                .id(tournament.getId())
                .nombre(tournament.getNombre())
                .codigo(tournament.getCodigo())
                .tipo(tournament.getTipo())
                .modalidad(tournament.getModalidad())
                .estado(tournament.getEstado())
                .courseId(tournament.getCourse().getId())
                .courseName(tournament.getCourse().getNombre())
                .cantidadHoyosJuego(tournament.getCantidadHoyosJuego())
                .teeMasculinoId(tournament.getTeeMasculino() != null ? tournament.getTeeMasculino().getId() : null)
                .teeFemeninoId(tournament.getTeeFemenino() != null ? tournament.getTeeFemenino().getId() : null)
                .fechaInicio(tournament.getFechaInicio())
                .fechaFin(tournament.getFechaFin())
                .horarioInicio(tournament.getHorarioInicio())
                .horarioCierre(tournament.getHorarioCierre())
                .limiteInscriptos(tournament.getLimiteInscriptos())
                .valorInscripcion(tournament.getValorInscripcion())
                .doublePoints(tournament.getDoublePoints())
                .controlCruzado(tournament.getControlCruzado())
                .currentInscriptos(inscriptos.intValue())
                .categories(categories)
                .prizes(prizes)
                .build();
    }

    /**
     * Si el torneo pertenece a una etapa de Torneo Administrativo, completa stage/admin
     * y opcionalmente la scoring config.
     */
    private void enrichWithAdminStage(TournamentDTO dto, boolean includeScoringConfig) {
        List<TournamentAdminStage> stages = tournamentAdminStageRepository.findByTournamentId(dto.getId());
        if (stages.isEmpty()) {
            return;
        }
        TournamentAdminStage stage = stages.get(0);
        Long adminId = stage.getTournamentAdmin().getId();
        dto.setStageId(stage.getId());
        dto.setStageName(stage.getNombre());
        dto.setTournamentAdminId(adminId);
        if (includeScoringConfig) {
            dto.setScoringConfig(
                    tournamentAdminScoringConfigService.getOrDefaultByTournamentAdminId(adminId));
        }
    }

    @Transactional
    public TournamentDTO startTournament(Long id) {
        Tournament tournament = tournamentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", "id", id));
        currentUserProvider.assertClubAccess(tournament.getCourse().getId());

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
        currentUserProvider.assertClubAccess(tournament.getCourse().getId());

        // Resolver tarjetas pendientes: completas → DELIVERED, incompletas → CANCELLED
        resolveScorecardsBeforeFinalize(tournament);

        tournament.setEstado("FINALIZED");
        tournament = tournamentRepository.save(tournament);
        log.info("Tournament {} finalized", id);
        return convertToDTO(tournament);
    }

    /**
     * Resuelve el estado de cada tarjeta al finalizar el torneo:
     *  - Jugador con hcp_activo=false                        → INACTIVE (no participa en puntuación/posiciones),
     *    incluso si la tarjeta ya estaba DELIVERED (el jugador pudo entregarla antes de quedar inactivo).
     *  - Tarjeta pendiente (IN_PROGRESS/PENDING_CONFIG) con todos los hoyos cargados → DELIVERED
     *  - Tarjeta pendiente con carga parcial o sin hoyos     → CANCELLED
     *
     * Las tarjetas ya CANCELLED, DISQUALIFIED o INACTIVE no se tocan. Las DELIVERED tampoco se
     * tocan salvo que el jugador tenga hcp_activo=false.
     */
    private void resolveScorecardsBeforeFinalize(Tournament tournament) {
        int holesRequired = resolveHolesRequiredForTournament(tournament);

        List<Scorecard> candidates = scorecardRepository
                .findByTournamentIdAndStatusIn(tournament.getId(),
                        List.of(ScorecardStatus.IN_PROGRESS, ScorecardStatus.PENDING_CONFIG, ScorecardStatus.DELIVERED));

        if (candidates.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now();
        int delivered = 0;
        int cancelled = 0;
        int inactivated = 0;
        List<Scorecard> toSave = new ArrayList<>();

        for (Scorecard scorecard : candidates) {
            boolean hcpActivo = scorecard.getPlayer() == null
                    || scorecard.getPlayer().getHcpActivo() == null
                    || scorecard.getPlayer().getHcpActivo();

            if (!hcpActivo) {
                scorecard.setStatus(ScorecardStatus.INACTIVE);
                if (scorecard.getDeliveredAt() == null) {
                    scorecard.setDeliveredAt(now);
                }
                inactivated++;
                toSave.add(scorecard);
                continue;
            }

            if (scorecard.getStatus() == ScorecardStatus.DELIVERED) {
                // Ya entregada y el jugador tiene el hcp activo: no se toca.
                continue;
            }

            if (isScorecardComplete(scorecard, holesRequired)) {
                scorecard.setStatus(ScorecardStatus.DELIVERED);
                scorecard.setDeliveredAt(now);
                delivered++;
            } else {
                scorecard.setStatus(ScorecardStatus.CANCELLED);
                if (scorecard.getDeliveredAt() == null) {
                    scorecard.setDeliveredAt(now);
                }
                cancelled++;
            }
            toSave.add(scorecard);
        }

        if (toSave.isEmpty()) return;

        scorecardRepository.saveAll(toSave);
        scorecardRepository.flush();
        log.info("Torneo {}: {} tarjeta(s) entregadas, {} canceladas, {} inactivas (hcp) al finalizar",
                tournament.getId(), delivered, cancelled, inactivated);
    }

    /**
     * Determina si una tarjeta tiene todos los hoyos requeridos con golpesPropio cargados.
     * Usa holeScoreRepository para cargar los holeScores (evita problemas de lazy loading).
     */
    private boolean isScorecardComplete(Scorecard scorecard, int holesRequired) {
        List<HoleScore> holeScores = holeScoreRepository.findByScorecardId(scorecard.getId());
        if (holeScores == null || holeScores.isEmpty()) {
            return false;
        }
        long filledHoles = holeScores.stream()
                .filter(hs -> hs.getGolpesPropio() != null && hs.getGolpesPropio() > 0)
                .count();
        return filledHoles >= holesRequired;
    }

    /**
     * Determina la cantidad de hoyos requeridos para el torneo.
     * Usa cantidadHoyosJuego del torneo; si no está definido, asume 18.
     */
    private int resolveHolesRequiredForTournament(Tournament tournament) {
        if (tournament.getCantidadHoyosJuego() != null && tournament.getCantidadHoyosJuego() > 0) {
            return tournament.getCantidadHoyosJuego();
        }
        return 18;
    }

    /**
     * Vuelve un torneo finalizado al estado en curso para permitir correcciones.
     */
    @Transactional
    public TournamentDTO reopenTournament(Long id) {
        Tournament tournament = tournamentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", "id", id));
        currentUserProvider.assertClubAccess(tournament.getCourse().getId());

        if (!"FINALIZED".equals(tournament.getEstado())) {
            throw new BadRequestException("Solo se puede habilitar un torneo que esté finalizado");
        }

        tournament.setEstado("IN_PROGRESS");
        tournament = tournamentRepository.save(tournament);
        log.info("Tournament {} reopened to IN_PROGRESS", id);
        return convertToDTO(tournament);
    }

    private TournamentCategoryDTO convertCategoryToDTO(TournamentCategory category) {
        return TournamentCategoryDTO.builder()
                .id(category.getId())
                .nombre(category.getNombre())
                .handicapMin(category.getHandicapMin())
                .handicapMax(category.getHandicapMax())
                .sexoCategoria(category.getSexoCategoria())
                .build();
    }

    private CourseTee resolveTournamentTee(Course course, Long teeId) {
        if (teeId == null) {
            return null;
        }

        CourseTee tee = courseTeeRepository.findById(teeId)
                .orElseThrow(() -> new ResourceNotFoundException("CourseTee", "id", teeId));
        if (!tee.getCourse().getId().equals(course.getId())) {
            throw new BadRequestException("El tee seleccionado no pertenece al campo del torneo");
        }
        return tee;
    }

    private void validateCantidadHoyosJuego(Integer cantidadHoyosJuego) {
        if (cantidadHoyosJuego == null) {
            return;
        }
        if (cantidadHoyosJuego != 9 && cantidadHoyosJuego != 18) {
            throw new BadRequestException("La cantidad de hoyos a jugar debe ser 9 o 18.");
        }
    }

    /**
     * Valida que, si ambos horarios están definidos, el horario de inicio sea anterior al de cierre.
     */
    private void validateHorarios(java.time.LocalTime horarioInicio, java.time.LocalTime horarioCierre) {
        if (horarioInicio != null && horarioCierre != null && !horarioInicio.isBefore(horarioCierre)) {
            throw new BadRequestException("El horario de inicio debe ser anterior al horario de cierre.");
        }
    }

    private void validateCategorySexes(List<TournamentCategoryDTO> categories) {
        if (categories == null) {
            return;
        }
        for (TournamentCategoryDTO category : categories) {
            normalizeCategorySex(category.getSexoCategoria());
        }
    }

    private String normalizeCategorySex(String categorySex) {
        if (categorySex == null || categorySex.trim().isBlank()) {
            return CATEGORY_SEX_MIXED;
        }

        String normalized = categorySex.trim().toUpperCase();
        if (!CATEGORY_SEX_MALE.equals(normalized)
                && !CATEGORY_SEX_FEMALE.equals(normalized)
                && !CATEGORY_SEX_MIXED.equals(normalized)) {
            throw new BadRequestException("El sexo de categoría es inválido. Debe ser M, F o X.");
        }
        return normalized;
    }

    private String normalizePlayerSex(String playerSex) {
        if (playerSex == null || playerSex.trim().isBlank()) {
            return CATEGORY_SEX_MIXED;
        }

        String normalized = playerSex.trim().toUpperCase();
        if (CATEGORY_SEX_MALE.equals(normalized) || CATEGORY_SEX_FEMALE.equals(normalized)) {
            return normalized;
        }
        return CATEGORY_SEX_MIXED;
    }

    private boolean categoryAppliesToPlayerSex(TournamentCategory category, String playerSex) {
        String categorySex = normalizeCategorySex(category.getSexoCategoria());
        if (CATEGORY_SEX_MIXED.equals(categorySex)) {
            return true;
        }
        return categorySex.equals(playerSex);
    }
}
