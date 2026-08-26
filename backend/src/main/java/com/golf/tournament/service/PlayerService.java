package com.golf.tournament.service;

import com.golf.tournament.dto.player.BulkUpdateAltaItem;
import com.golf.tournament.dto.player.BulkUpdateHandicapChangeItem;
import com.golf.tournament.dto.player.BulkUpdateResponse;
import com.golf.tournament.dto.player.CreatePlayerRequest;
import com.golf.tournament.dto.player.PlayerDTO;
import com.golf.tournament.exception.BadRequestException;
import com.golf.tournament.exception.DuplicateResourceException;
import com.golf.tournament.exception.ResourceNotFoundException;
import com.golf.tournament.model.Course;
import com.golf.tournament.model.Player;
import com.golf.tournament.repository.CourseRepository;
import com.golf.tournament.repository.PlayerRepository;
import com.golf.tournament.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final CourseRepository courseRepository;
    private final CurrentUserProvider currentUserProvider;

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
        String trimmedMatricula = trim(matricula);
        Player player = playerRepository.findByMatricula(trimmedMatricula)
                .orElseThrow(() -> new ResourceNotFoundException("Player", "matricula", trimmedMatricula));
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
        String matricula = trim(request.getMatricula());

        if (playerRepository.existsByMatricula(matricula)) {
            throw new DuplicateResourceException("Player", "matricula", matricula);
        }

        String clubOrigen = trim(request.getClubOrigen());
        Player player = Player.builder()
                .nombre(trim(request.getNombre()))
                .apellido(trim(request.getApellido()))
                .email(trim(request.getEmail()))
                .matricula(matricula)
                .fechaNacimiento(request.getFechaNacimiento())
                .sexo(trim(request.getSexo()))
                .handicapIndex(request.getHandicapIndex())
                .telefono(trim(request.getTelefono()))
                .clubOrigen(clubOrigen)
                .course(resolveCourseFromClubOrigen(clubOrigen))
                .hcpActivo(request.getHcpActivo() != null ? request.getHcpActivo() : true)
                .build();

        player = playerRepository.save(player);
        log.info("Player created with id: {}", player.getId());
        return convertToDTO(player);
    }

    @Transactional
    public PlayerDTO updatePlayer(Long id, CreatePlayerRequest request) {
        Player player = playerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Player", "id", id));

        String matricula = trim(request.getMatricula());

        if (!player.getMatricula().equals(matricula) &&
                playerRepository.existsByMatricula(matricula)) {
            throw new DuplicateResourceException("Player", "matricula", matricula);
        }

        player.setNombre(trim(request.getNombre()));
        player.setApellido(trim(request.getApellido()));
        player.setEmail(trim(request.getEmail()));
        player.setMatricula(matricula);
        player.setFechaNacimiento(request.getFechaNacimiento());
        player.setSexo(trim(request.getSexo()));
        player.setHandicapIndex(request.getHandicapIndex());
        player.setTelefono(trim(request.getTelefono()));
        String clubOrigen = trim(request.getClubOrigen());
        player.setClubOrigen(clubOrigen);
        player.setCourse(resolveCourseFromClubOrigen(clubOrigen));
        player.setHcpActivo(request.getHcpActivo() != null ? request.getHcpActivo() : true);

        player = playerRepository.save(player);
        log.info("Player updated with id: {}", player.getId());
        return convertToDTO(player);
    }

    private String trim(String value) {
        return value != null ? value.trim() : null;
    }

    /**
     * Resuelve el club (course) correspondiente al texto libre clubOrigen, creándolo si
     * todavía no existe (match case-insensitive por nombre). No afecta el acceso a los
     * jugadores, que siguen siendo visibles en toda la app; solo habilita el filtro
     * "de mi club / todos" en la UI.
     */
    private Course resolveCourseFromClubOrigen(String clubOrigen) {
        if (clubOrigen == null || clubOrigen.isBlank()) {
            return null;
        }
        String normalized = toTitleCase(clubOrigen);
        return courseRepository.findByNombreIgnoreCase(normalized)
                .orElseGet(() -> {
                    Course newCourse = Course.builder()
                            .nombre(normalized)
                            .pais("Argentina")
                            .cantidadHoyos(18)
                            .build();
                    Course saved = courseRepository.save(newCourse);
                    log.info("Club creado automáticamente a partir de clubOrigen: {} (id {})", normalized, saved.getId());
                    return saved;
                });
    }

    /**
     * Convierte un texto a Title Case (ej. "CARANDAY GOLF CLUB" -> "Caranday Golf Club"),
     * equivalente al initcap() usado en las migraciones de datos.
     */
    private String toTitleCase(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        StringBuilder result = new StringBuilder(trimmed.length());
        boolean capitalizeNext = true;
        for (char c : trimmed.toCharArray()) {
            if (Character.isLetter(c)) {
                result.append(capitalizeNext ? Character.toUpperCase(c) : Character.toLowerCase(c));
                capitalizeNext = false;
            } else {
                result.append(c);
                capitalizeNext = true;
            }
        }
        return result.toString();
    }

    @Transactional
    public void deletePlayer(Long id) {
        if (!playerRepository.existsById(id)) {
            throw new ResourceNotFoundException("Player", "id", id);
        }
        currentUserProvider.assertCanDelete();
        playerRepository.deleteById(id);
        log.info("Player deleted with id: {}", id);
    }

    @Transactional
    public BulkUpdateResponse bulkUpdatePlayers(MultipartFile file) {
        BulkUpdateResponse response = BulkUpdateResponse.builder()
                .actualizados(0)
                .creados(0)
                .matriculasNoProcesadas(new ArrayList<>())
                .altas(new ArrayList<>())
                .cambiosHandicapIndex(new ArrayList<>())
                .build();
        int actualizados = 0;
        int creados = 0;
        List<String> matriculasNoProcesadas = new ArrayList<>();
        List<BulkUpdateAltaItem> altas = new ArrayList<>();
        List<BulkUpdateHandicapChangeItem> cambiosHandicapIndex = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            
            // Obtener los índices de las columnas desde la primera fila (headers)
            Row headerRow = sheet.getRow(0);
            Map<String, Integer> columnIndexMap = getColumnIndexMap(headerRow);
            
            // Procesar cada fila (empezando desde la fila 1, después del header)
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                
                try {
                    // Extraer datos de las columnas
                    String matricula = getCellValueAsString(row, columnIndexMap.get("Matricula"));
                    String nombre = getCellValueAsString(row, columnIndexMap.get("Nombre"));
                    String apellido = getCellValueAsString(row, columnIndexMap.get("Apellido"));
                    String handicapStr = getCellValueAsString(row, columnIndexMap.get("HandicapIndex"));
                    
                    // Validar campos obligatorios
                    if (matricula == null || matricula.isBlank() ||
                        nombre == null || nombre.isBlank() ||
                        apellido == null || apellido.isBlank() ||
                        handicapStr == null || handicapStr.isBlank()) {
                        matriculasNoProcesadas.add(matricula != null && !matricula.isBlank() ? matricula : "Fila " + (i + 1));
                        continue;
                    }
                    
                    BigDecimal handicapIndex = new BigDecimal(handicapStr);
                    
                    // Extraer campos opcionales
                    String telefono = getCellValueAsString(row, columnIndexMap.get("Tel_Movil"));
                    String email = getCellValueAsString(row, columnIndexMap.get("Email"));
                    String clubOrigen = toTitleCase(getCellValueAsString(row, columnIndexMap.get("Club")));
                    String nacimientoStr = getCellValueAsString(row, columnIndexMap.get("Nacimiento"));
                    
                    LocalDate fechaNacimiento = null;
                    if (nacimientoStr != null && !nacimientoStr.isBlank()) {
                        fechaNacimiento = parseDateDDMMYYYY(nacimientoStr);
                    }
                    
                    // Buscar jugador existente por matrícula
                    Optional<Player> existingPlayerOpt = playerRepository.findByMatricula(matricula);
                    
                    if (existingPlayerOpt.isPresent()) {
                        // ACTUALIZAR solo si hay cambios
                        Player player = existingPlayerOpt.get();
                        boolean updated = false;
                        
                        if (!player.getNombre().equals(nombre)) {
                            player.setNombre(nombre);
                            updated = true;
                        }
                        if (!player.getApellido().equals(apellido)) {
                            player.setApellido(apellido);
                            updated = true;
                        }
                        if (player.getHandicapIndex().compareTo(handicapIndex) != 0) {
                            BigDecimal handicapAnterior = player.getHandicapIndex();
                            player.setHandicapIndex(handicapIndex);
                            updated = true;
                            cambiosHandicapIndex.add(BulkUpdateHandicapChangeItem.builder()
                                    .matricula(matricula)
                                    .nombre(nombre)
                                    .apellido(apellido)
                                    .handicapAnterior(handicapAnterior)
                                    .handicapNuevo(handicapIndex)
                                    .build());
                        }
                        if (!Objects.equals(player.getTelefono(), telefono)) {
                            player.setTelefono(telefono);
                            updated = true;
                        }
                        if (!Objects.equals(player.getEmail(), email)) {
                            player.setEmail(email);
                            updated = true;
                        }
                        if (!Objects.equals(player.getClubOrigen(), clubOrigen)) {
                            player.setClubOrigen(clubOrigen);
                            player.setCourse(resolveCourseFromClubOrigen(clubOrigen));
                            updated = true;
                        }
                        if (!Objects.equals(player.getFechaNacimiento(), fechaNacimiento)) {
                            player.setFechaNacimiento(fechaNacimiento);
                            updated = true;
                        }
                        
                        if (updated) {
                            playerRepository.save(player);
                            actualizados++;
                            log.info("Player updated: {}", matricula);
                        }
                    } else {
                        // CREAR nuevo jugador
                        Player newPlayer = Player.builder()
                                .matricula(matricula)
                                .nombre(nombre)
                                .apellido(apellido)
                                .sexo("M")
                                .handicapIndex(handicapIndex)
                                .telefono(telefono)
                                .email(email)
                                .clubOrigen(clubOrigen)
                                .course(resolveCourseFromClubOrigen(clubOrigen))
                                .fechaNacimiento(fechaNacimiento)
                                .build();
                        
                        playerRepository.save(newPlayer);
                        creados++;
                        altas.add(BulkUpdateAltaItem.builder()
                                .matricula(matricula)
                                .nombre(nombre)
                                .apellido(apellido)
                                .build());
                        log.info("Player created: {}", matricula);
                    }
                    
                } catch (Exception e) {
                    log.error("Error processing row {}: {}", i + 1, e.getMessage());
                    String matricula = getCellValueAsString(row, columnIndexMap.get("Matricula"));
                    matriculasNoProcesadas.add(matricula != null && !matricula.isBlank() ? matricula : "Fila " + (i + 1));
                }
            }
            
            response.setActualizados(actualizados);
            response.setCreados(creados);
            response.setMatriculasNoProcesadas(matriculasNoProcesadas);
            response.setAltas(altas);
            response.setCambiosHandicapIndex(cambiosHandicapIndex);
            
        } catch (Exception e) {
            log.error("Error processing Excel file: {}", e.getMessage());
            throw new BadRequestException("Error procesando archivo Excel: " + e.getMessage());
        }
        
        return response;
    }

    private Map<String, Integer> getColumnIndexMap(Row headerRow) {
        Map<String, Integer> map = new HashMap<>();
        for (Cell cell : headerRow) {
            String columnName = cell.getStringCellValue().trim();
            map.put(columnName, cell.getColumnIndex());
        }
        return map;
    }

    private String getCellValueAsString(Row row, Integer columnIndex) {
        if (columnIndex == null) return null;
        
        Cell cell = row.getCell(columnIndex);
        if (cell == null) return null;
        
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    LocalDate date = cell.getLocalDateTimeCellValue().toLocalDate();
                    yield date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                } else {
                    // Para números, verificar si es entero o decimal
                    double numericValue = cell.getNumericCellValue();
                    if (numericValue == (long) numericValue) {
                        yield String.valueOf((long) numericValue);
                    } else {
                        yield String.valueOf(numericValue);
                    }
                }
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }

    private LocalDate parseDateDDMMYYYY(String dateStr) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            return LocalDate.parse(dateStr, formatter);
        } catch (Exception e) {
            log.error("Error parsing date: {}", dateStr);
            return null;
        }
    }

    private PlayerDTO convertToDTO(Player player) {
        return PlayerDTO.builder()
                .id(player.getId())
                .nombre(player.getNombre())
                .apellido(player.getApellido())
                .email(player.getEmail())
                .matricula(player.getMatricula())
                .fechaNacimiento(player.getFechaNacimiento())
                .sexo(player.getSexo())
                .handicapIndex(player.getHandicapIndex())
                .telefono(player.getTelefono())
                .clubOrigen(player.getClubOrigen())
                .courseId(player.getCourse() != null ? player.getCourse().getId() : null)
                .courseName(player.getCourse() != null ? player.getCourse().getNombre() : null)
                .hcpActivo(player.getHcpActivo() != null ? player.getHcpActivo() : true)
                .build();
    }
}
