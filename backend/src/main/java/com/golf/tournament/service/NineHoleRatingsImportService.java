package com.golf.tournament.service;

import com.golf.tournament.dto.course.ImportNineHoleRatingsResponse;
import com.golf.tournament.dto.course.ImportNineHoleRatingsTeeResultDTO;
import com.golf.tournament.exception.BadRequestException;
import com.golf.tournament.exception.ResourceNotFoundException;
import com.golf.tournament.model.Course;
import com.golf.tournament.model.CourseTee;
import com.golf.tournament.repository.CourseRepository;
import com.golf.tournament.repository.CourseTeeRepository;
import com.golf.tournament.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Importa la planilla "Reporte de Tarjeta" que exporta la AAG (Asociación Argentina de Golf),
 * la cual trae, para cada Salida (tee) de un campo, la Calificación (Course Rating) y el Slope
 * Rating de Ida (hoyos 1-9), Vuelta (hoyos 10-18) y Total (18 hoyos).
 *
 * <p>A diferencia de {@link HandicapConversionService} y {@link HoleDistanceImportService}, este
 * archivo no tiene una fila de encabezados con columnas: es un reporte con bloques repetidos (uno
 * por Salida), cada uno con líneas de texto del tipo "Categoría: Damas", "Salida: Rojo Damas",
 * "Calificación Ida: 36.0", "Slope Ida: 120", etc. Por eso el parseo es una máquina de estados
 * que recorre la columna A fila por fila.</p>
 *
 * <p>El matching contra los tees de salida existentes es automático (por nombre + género, igual
 * que en los otros importadores) y no requiere que el usuario seleccione tees manualmente: si no
 * existe un tee para una Salida del archivo, se crea automáticamente (comportamiento controlado
 * por {@code createMissing}, que por defecto es true).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NineHoleRatingsImportService {

    private static final List<String> GENERO_SUFFIXES = List.of("caballeros", "damas", "masculino", "femenino");

    private final CourseRepository courseRepository;
    private final CourseTeeRepository courseTeeRepository;
    private final CurrentUserProvider currentUserProvider;

    @Transactional
    public ImportNineHoleRatingsResponse importRatings(Long courseId, boolean createMissing, MultipartFile file) {
        Course course = requireCourse(courseId);
        List<ParsedTeeBlock> blocks = parseWorkbook(file);

        if (blocks.isEmpty()) {
            throw new BadRequestException(
                    "No se encontraron Salidas en la planilla. Verifique que sea el 'Reporte de Tarjeta' exportado " +
                            "por la AAG, con secciones 'Salida:', 'Calificación Ida/Vuelta:' y 'Slope Ida/Vuelta:'.");
        }

        List<CourseTee> existingTees = new ArrayList<>(courseTeeRepository.findByCourseId(courseId));
        List<ImportNineHoleRatingsTeeResultDTO> results = new ArrayList<>();

        for (ParsedTeeBlock block : blocks) {
            CourseTee tee = existingTees.stream()
                    .filter(t -> matchKey(t.getNombre(), t.getGenero()).equals(matchKey(block.nombre(), block.genero())))
                    .findFirst()
                    .orElse(null);

            boolean created = false;
            if (tee == null) {
                if (!createMissing) {
                    results.add(ImportNineHoleRatingsTeeResultDTO.builder()
                            .teeNombre(block.nombre())
                            .genero(block.genero())
                            .imported(false)
                            .created(false)
                            .message("No existe un tee '" + block.nombre() + "' (" + generoLabel(block.genero())
                                    + ") en este campo. No se importó.")
                            .build());
                    continue;
                }
                tee = courseTeeRepository.save(CourseTee.builder()
                        .course(course)
                        .nombre(block.nombre())
                        .genero(block.genero())
                        .active(true)
                        .build());
                existingTees.add(tee);
                created = true;
                log.info("Tee creado automáticamente en campo {}: {} ({})", courseId, tee.getNombre(), tee.getGenero());
            }

            tee.setCourseRatingIda(block.courseRatingIda());
            tee.setCourseRatingVuelta(block.courseRatingVuelta());
            tee.setSlopeRatingIda(block.slopeRatingIda());
            tee.setSlopeRatingVuelta(block.slopeRatingVuelta());
            tee = courseTeeRepository.save(tee);

            log.info("Calificación 9 hoyos importada para tee {} ({}): CR Ida={}, Slope Ida={}",
                    tee.getId(), tee.getNombre(), tee.getCourseRatingIda(), tee.getSlopeRatingIda());

            results.add(ImportNineHoleRatingsTeeResultDTO.builder()
                    .teeId(tee.getId())
                    .teeNombre(tee.getNombre())
                    .genero(tee.getGenero())
                    .imported(true)
                    .created(created)
                    .courseRatingIda(tee.getCourseRatingIda())
                    .courseRatingVuelta(tee.getCourseRatingVuelta())
                    .slopeRatingIda(tee.getSlopeRatingIda())
                    .slopeRatingVuelta(tee.getSlopeRatingVuelta())
                    .message(created ? "Tee creado e importado correctamente" : "Actualizado correctamente")
                    .build());
        }

        return ImportNineHoleRatingsResponse.builder().tees(results).build();
    }

    // ------------------------------------------------------------------
    // Parseo del archivo
    // ------------------------------------------------------------------

    private List<ParsedTeeBlock> parseWorkbook(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("El archivo está vacío");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new BadRequestException("El archivo debe ser formato .xlsx");
        }

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new BadRequestException("El archivo no contiene hojas");
            }
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            List<ParsedTeeBlock> blocks = new ArrayList<>();
            String currentGenero = null;
            String currentNombreRaw = null;
            BigDecimal ratingIda = null;
            BigDecimal ratingVuelta = null;
            Integer slopeIda = null;
            Integer slopeVuelta = null;

            for (int i = 0; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                String text = cellString(row, formatter);
                if (text == null || text.isBlank()) {
                    continue;
                }
                String normalized = normalizeLabel(text);

                if (normalized.startsWith("categoria:")) {
                    String genero = mapGenero(afterColon(text));
                    if (genero == null) {
                        throw new BadRequestException(
                                "Categoría desconocida en la fila " + (i + 1) + ": " + afterColon(text));
                    }
                    currentGenero = genero;
                } else if (normalized.startsWith("salida:")) {
                    currentNombreRaw = afterColon(text);
                    ratingIda = null;
                    ratingVuelta = null;
                    slopeIda = null;
                    slopeVuelta = null;
                } else if (normalized.startsWith("calificacion ida:")) {
                    ratingIda = parseDecimalOrNull(afterColon(text), "Calificación Ida", i + 1);
                } else if (normalized.startsWith("calificacion vuelta:")) {
                    ratingVuelta = parseDecimalOrNull(afterColon(text), "Calificación Vuelta", i + 1);
                } else if (normalized.startsWith("slope ida:")) {
                    slopeIda = parseIntOrNull(afterColon(text), "Slope Ida", i + 1);
                } else if (normalized.startsWith("slope vuelta:")) {
                    slopeVuelta = parseIntOrNull(afterColon(text), "Slope Vuelta", i + 1);
                } else if (normalized.startsWith("slope total:")) {
                    if (currentNombreRaw != null && currentGenero != null) {
                        String nombre = stripGeneroSuffix(currentNombreRaw);
                        blocks.add(new ParsedTeeBlock(nombre, currentGenero, ratingIda, ratingVuelta, slopeIda, slopeVuelta));
                    }
                    currentNombreRaw = null;
                }
            }

            return blocks;
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error procesando planilla de calificación 9 hoyos: {}", e.getMessage(), e);
            throw new BadRequestException("Error procesando archivo Excel: " + e.getMessage());
        }
    }

    private Course requireCourse(Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", courseId));
        currentUserProvider.assertClubAccess(course.getId());
        return course;
    }

    private String cellString(Row row, DataFormatter formatter) {
        var cell = row.getCell(0);
        if (cell == null) {
            return null;
        }
        String value = formatter.formatCellValue(cell);
        return value != null ? value.trim() : null;
    }

    private String afterColon(String text) {
        int idx = text.indexOf(':');
        if (idx < 0) {
            return "";
        }
        return text.substring(idx + 1).trim();
    }

    private String normalizeLabel(String value) {
        return value.trim().toLowerCase(Locale.ROOT)
                .replace("á", "a").replace("é", "e").replace("í", "i")
                .replace("ó", "o").replace("ú", "u");
    }

    /**
     * Quita el sufijo de género (ej. "Damas", "Caballeros") del nombre de la Salida del archivo
     * (ej. "Rojo Damas" -&gt; "Rojo"), para poder matchear contra {@code CourseTee.nombre}, que
     * en esta app guarda solo el color/nombre del tee, sin el género.
     */
    private String stripGeneroSuffix(String raw) {
        String trimmed = raw.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        for (String suffix : GENERO_SUFFIXES) {
            if (lower.endsWith(suffix)) {
                String result = trimmed.substring(0, trimmed.length() - suffix.length()).trim();
                if (!result.isEmpty()) {
                    return result;
                }
            }
        }
        return trimmed;
    }

    private String matchKey(String nombre, String genero) {
        String normalizedName = nombre == null ? "" : nombre.trim().toLowerCase(Locale.ROOT);
        String normalizedGenero = genero == null ? "M" : genero;
        return normalizedName + "|" + normalizedGenero;
    }

    private String mapGenero(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.equals("m") || value.equals("caballeros") || value.equals("masculino")) {
            return "M";
        }
        if (value.equals("f") || value.equals("damas") || value.equals("femenino")) {
            return "F";
        }
        return null;
    }

    private String generoLabel(String genero) {
        return "F".equals(genero) ? "Damas" : "Caballeros";
    }

    private BigDecimal parseDecimalOrNull(String raw, String campo, int rowNumber) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.replace(',', '.').trim()).setScale(1, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            log.warn("Valor numérico inválido en {} (fila {}): {}", campo, rowNumber, raw);
            return null;
        }
    }

    private Integer parseIntOrNull(String raw, String campo, int rowNumber) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.replace(',', '.').trim()).setScale(0, RoundingMode.HALF_UP).intValue();
        } catch (NumberFormatException e) {
            log.warn("Valor numérico inválido en {} (fila {}): {}", campo, rowNumber, raw);
            return null;
        }
    }

    private record ParsedTeeBlock(String nombre, String genero, BigDecimal courseRatingIda,
                                   BigDecimal courseRatingVuelta, Integer slopeRatingIda, Integer slopeRatingVuelta) {
    }
}
