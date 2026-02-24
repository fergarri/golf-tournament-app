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
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TournamentService {

    private final TournamentRepository tournamentRepository;
    private final CourseRepository courseRepository;
    private final TournamentCategoryRepository tournamentCategoryRepository;
    private final TournamentInscriptionRepository tournamentInscriptionRepository;
    private final ScorecardRepository scorecardRepository;

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

        String codigo = generateUniqueCodigo();

        Boolean doublePoints = "FRUTALES".equals(request.getTipo()) && Boolean.TRUE.equals(request.getDoublePoints());

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
                .doublePoints(doublePoints)
                .build();

        tournament = tournamentRepository.save(tournament);

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

        tournament.setNombre(request.getNombre());
        tournament.setTipo(request.getTipo());
        tournament.setModalidad(request.getModalidad());
        tournament.setCourse(course);
        tournament.setFechaInicio(request.getFechaInicio());
        tournament.setFechaFin(request.getFechaFin());
        tournament.setLimiteInscriptos(request.getLimiteInscriptos());
        tournament.setValorInscripcion(request.getValorInscripcion());
        tournament.setDoublePoints("FRUTALES".equals(request.getTipo()) && Boolean.TRUE.equals(request.getDoublePoints()));
        tournament = tournamentRepository.save(tournament);

        // Smart UPDATE/CREATE/DELETE of categories
        boolean categoriesChanged = updateTournamentCategories(tournament, request.getCategories());

        // If categories changed, reassign all inscriptions
        if (categoriesChanged) {
            reassignInscriptionCategories(tournament.getId());
        }

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
        if (!tournamentRepository.existsById(id)) {
            throw new ResourceNotFoundException("Tournament", "id", id);
        }
        tournamentRepository.deleteById(id);
        log.info("Tournament deleted with id: {}", id);
    }

    /**
     * Finds the appropriate category for a given handicap course value.
     * Returns null if the handicap doesn't fall within any category range.
     */
    private TournamentCategory findCategoryForHandicap(java.math.BigDecimal handicapCourse, 
                                                       List<TournamentCategory> categories) {
        if (handicapCourse == null || categories == null || categories.isEmpty()) {
            return null;
        }

        for (TournamentCategory category : categories) {
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
            // Try to find scorecard for this player
            Scorecard scorecard = scorecardRepository
                    .findByTournamentIdAndPlayerId(tournamentId, inscription.getPlayer().getId())
                    .orElse(null);
            
            TournamentCategory newCategory = null;
            
            // Only assign category if scorecard exists AND has handicapCourse
            if (scorecard != null && scorecard.getHandicapCourse() != null) {
                newCategory = findCategoryForHandicap(scorecard.getHandicapCourse(), categories);
                
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
                .doublePoints(tournament.getDoublePoints())
                .currentInscriptos(inscriptos.intValue())
                .categories(categories)
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

        // Convert any in-progress scorecards to CANCELLED before finalizing.
        List<Scorecard> inProgressScorecards = scorecardRepository
                .findByTournamentIdAndStatus(id, ScorecardStatus.IN_PROGRESS);

        if (!inProgressScorecards.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            for (Scorecard scorecard : inProgressScorecards) {
                scorecard.setStatus(ScorecardStatus.CANCELLED);
                if (scorecard.getDeliveredAt() == null) {
                    scorecard.setDeliveredAt(now);
                }
            }
            scorecardRepository.saveAll(inProgressScorecards);
            scorecardRepository.flush();
        }

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
