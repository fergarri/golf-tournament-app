package com.golf.tournament.service;

import com.golf.tournament.dto.course.HandicapConversionDTO;
import com.golf.tournament.dto.course.ImportHandicapConversionResponse;
import com.golf.tournament.dto.course.ImportHandicapConversionTeeResultDTO;
import com.golf.tournament.dto.course.MissingCourseTeeDTO;
import com.golf.tournament.dto.course.PreviewHandicapImportResponse;
import com.golf.tournament.dto.course.TeeHandicapTableDTO;
import com.golf.tournament.exception.BadRequestException;
import com.golf.tournament.exception.ResourceNotFoundException;
import com.golf.tournament.model.Course;
import com.golf.tournament.model.CourseTee;
import com.golf.tournament.model.HandicapConversion;
import com.golf.tournament.repository.CourseRepository;
import com.golf.tournament.repository.CourseTeeRepository;
import com.golf.tournament.repository.HandicapConversionRepository;
import com.golf.tournament.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HandicapConversionService {

    private final CourseRepository courseRepository;
    private final CourseTeeRepository courseTeeRepository;
    private final HandicapConversionRepository handicapConversionRepository;
    private final CurrentUserProvider currentUserProvider;

    @Transactional(readOnly = true)
    public List<TeeHandicapTableDTO> getTablesForCourse(Long courseId) {
        if (!courseRepository.existsById(courseId)) {
            throw new ResourceNotFoundException("Course", "id", courseId);
        }
        currentUserProvider.assertClubAccess(courseId);

        List<CourseTee> tees = courseTeeRepository.findByCourseId(courseId);
        Map<Long, List<HandicapConversion>> byTee = handicapConversionRepository
                .findByCourseId(courseId)
                .stream()
                .collect(Collectors.groupingBy(hc -> hc.getTee().getId(), LinkedHashMap::new, Collectors.toList()));

        return tees.stream()
                .map(tee -> TeeHandicapTableDTO.builder()
                        .teeId(tee.getId())
                        .nombre(tee.getNombre())
                        .grupo(tee.getGrupo())
                        .genero(tee.getGenero() != null ? tee.getGenero() : "M")
                        .active(tee.getActive())
                        .conversions(byTee.getOrDefault(tee.getId(), List.of()).stream()
                                .map(this::toDto)
                                .collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PreviewHandicapImportResponse previewImport(Long courseId, MultipartFile file) {
        Course course = requireCourse(courseId);
        Map<String, FileTeeData> fileTees = parseWorkbook(file);
        List<MissingCourseTeeDTO> missing = findMissingTees(course.getId(), fileTees);
        return PreviewHandicapImportResponse.builder().missingTees(missing).build();
    }

    @Transactional
    public ImportHandicapConversionResponse importTables(Long courseId, List<Long> teeIds,
                                                        boolean createMissing, MultipartFile file) {
        Course course = requireCourse(courseId);
        Map<String, FileTeeData> fileTees = parseWorkbook(file);

        List<Long> idsToImport = new ArrayList<>(teeIds != null ? teeIds : List.of());
        if (createMissing) {
            for (MissingCourseTeeDTO missing : findMissingTees(course.getId(), fileTees)) {
                CourseTee created = courseTeeRepository.save(CourseTee.builder()
                        .course(course)
                        .nombre(missing.getNombre())
                        .genero(missing.getGenero())
                        .active(true)
                        .build());
                idsToImport.add(created.getId());
                log.info("Tee creado automáticamente en campo {}: {} ({})",
                        courseId, created.getNombre(), created.getGenero());
            }
        }

        idsToImport = idsToImport.stream().distinct().collect(Collectors.toList());
        if (idsToImport.isEmpty()) {
            throw new BadRequestException("Debe seleccionar al menos un tee de salida");
        }

        List<ImportHandicapConversionTeeResultDTO> results = new ArrayList<>();
        for (Long teeId : idsToImport) {
            CourseTee tee = courseTeeRepository.findById(teeId)
                    .orElseThrow(() -> new ResourceNotFoundException("CourseTee", "id", teeId));
            if (!tee.getCourse().getId().equals(courseId)) {
                throw new BadRequestException("El tee seleccionado no pertenece al campo");
            }
            if (!Boolean.TRUE.equals(tee.getActive())) {
                results.add(skipResult(tee));
                continue;
            }

            FileTeeData fileTee = fileTees.get(matchKey(tee.getNombre(), tee.getGenero()));
            List<ParsedRow> matched = fileTee != null ? fileTee.rows : List.of();
            if (matched.isEmpty()) {
                results.add(skipResult(tee));
                continue;
            }

            handicapConversionRepository.deleteByTeeId(tee.getId());
            handicapConversionRepository.flush();

            LocalDateTime now = LocalDateTime.now();
            List<HandicapConversion> toSave = matched.stream()
                    .map(row -> HandicapConversion.builder()
                            .tee(tee)
                            .hcpIndexFrom(row.from)
                            .hcpIndexTo(row.to)
                            .courseHandicap(row.courseHandicap)
                            .createdAt(now)
                            .build())
                    .collect(Collectors.toList());
            handicapConversionRepository.saveAll(toSave);

            log.info("Importadas {} filas de HCP Course para tee {} ({})", matched.size(), tee.getId(), tee.getNombre());
            results.add(ImportHandicapConversionTeeResultDTO.builder()
                    .teeId(tee.getId())
                    .teeNombre(tee.getNombre())
                    .genero(tee.getGenero())
                    .matchedRows(matched.size())
                    .imported(true)
                    .message(matched.size() + " filas importadas")
                    .build());
        }

        return ImportHandicapConversionResponse.builder().tees(results).build();
    }

    private Course requireCourse(Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", courseId));
        currentUserProvider.assertClubAccess(course.getId());
        return course;
    }

    private List<MissingCourseTeeDTO> findMissingTees(Long courseId, Map<String, FileTeeData> fileTees) {
        List<CourseTee> existing = courseTeeRepository.findByCourseId(courseId);
        return fileTees.values().stream()
                .filter(fileTee -> existing.stream().noneMatch(tee ->
                        matchKey(tee.getNombre(), tee.getGenero()).equals(matchKey(fileTee.nombre, fileTee.genero))))
                .map(fileTee -> MissingCourseTeeDTO.builder()
                        .nombre(fileTee.nombre)
                        .genero(fileTee.genero)
                        .build())
                .collect(Collectors.toList());
    }

    private ImportHandicapConversionTeeResultDTO skipResult(CourseTee tee) {
        return ImportHandicapConversionTeeResultDTO.builder()
                .teeId(tee.getId())
                .teeNombre(tee.getNombre())
                .genero(tee.getGenero())
                .matchedRows(0)
                .imported(false)
                .message("0 filas, no se modificó")
                .build();
    }

    private Map<String, FileTeeData> parseWorkbook(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("El archivo está vacío");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new BadRequestException("El archivo debe ser formato .xlsx");
        }
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = resolveSheet(workbook);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new BadRequestException("La planilla no tiene encabezados");
            }

            Map<String, Integer> columns = headerIndexMap(headerRow);
            Integer teeCol = firstPresent(columns, "tee_name", "tee", "nombre");
            Integer generoCol = firstPresent(columns, "genero", "género", "sexo");
            Integer fromCol = firstPresent(columns, "hci_i_desde", "hcp_index_desde", "hcp index desde");
            Integer toCol = firstPresent(columns, "hci_i_hasta", "hcp_index_hasta", "hcp index hasta");
            Integer hcpCol = firstPresent(columns, "hco_course_100", "hcp_course", "course_handicap", "hcp course 100%");

            if (teeCol == null || generoCol == null || fromCol == null || toCol == null || hcpCol == null) {
                throw new BadRequestException(
                        "La planilla debe tener las columnas tee_name, genero, HCI_I_DESDE, HCI_I_HASTA y HCO_COURSE_100");
            }

            DataFormatter formatter = new DataFormatter();
            Map<String, FileTeeData> byKey = new LinkedHashMap<>();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                String teeName = cellString(row, teeCol, formatter);
                String generoRaw = cellString(row, generoCol, formatter);
                if (teeName == null || teeName.isBlank() || generoRaw == null || generoRaw.isBlank()) {
                    continue;
                }

                String genero = mapGenero(generoRaw);
                if (genero == null) {
                    throw new BadRequestException("Género inválido en la fila " + (i + 1) + ": " + generoRaw);
                }

                BigDecimal from = parseDecimal(cellString(row, fromCol, formatter), "HCI_I_DESDE", i + 1);
                BigDecimal to = parseDecimal(cellString(row, toCol, formatter), "HCI_I_HASTA", i + 1);
                Integer courseHcp = parseCourseHandicap(cellString(row, hcpCol, formatter), i + 1);
                if (from.compareTo(to) > 0) {
                    throw new BadRequestException("Rango inválido en la fila " + (i + 1) + ": DESDE mayor que HASTA");
                }

                String key = matchKey(teeName, genero);
                FileTeeData data = byKey.computeIfAbsent(key, k -> new FileTeeData(teeName.trim(), genero, new ArrayList<>()));
                data.rows.add(new ParsedRow(from, to, courseHcp));
            }

            return byKey;
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error procesando planilla de HCP Course: {}", e.getMessage(), e);
            throw new BadRequestException("Error procesando archivo Excel: " + e.getMessage());
        }
    }

    private Sheet resolveSheet(Workbook workbook) {
        Sheet named = workbook.getSheet("CourseHandicap");
        if (named != null) {
            return named;
        }
        if (workbook.getNumberOfSheets() == 0) {
            throw new BadRequestException("El archivo no contiene hojas");
        }
        return workbook.getSheetAt(0);
    }

    private Map<String, Integer> headerIndexMap(Row headerRow) {
        Map<String, Integer> map = new HashMap<>();
        DataFormatter formatter = new DataFormatter();
        for (Cell cell : headerRow) {
            String name = formatter.formatCellValue(cell);
            if (name != null && !name.isBlank()) {
                map.put(normalizeHeader(name), cell.getColumnIndex());
            }
        }
        return map;
    }

    private Integer firstPresent(Map<String, Integer> columns, String... keys) {
        for (String key : keys) {
            Integer idx = columns.get(normalizeHeader(key));
            if (idx != null) {
                return idx;
            }
        }
        return null;
    }

    private String normalizeHeader(String value) {
        return value.trim().toLowerCase(Locale.ROOT)
                .replace("á", "a").replace("é", "e").replace("í", "i")
                .replace("ó", "o").replace("ú", "u");
    }

    private String cellString(Row row, int columnIndex, DataFormatter formatter) {
        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            return null;
        }
        String value = formatter.formatCellValue(cell);
        return value != null ? value.trim() : null;
    }

    private String matchKey(String nombre, String genero) {
        String normalizedName = nombre == null ? "" : nombre.trim().toLowerCase(Locale.ROOT);
        String normalizedGenero = mapGenero(genero);
        if (normalizedGenero == null) {
            normalizedGenero = "M";
        }
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

    private BigDecimal parseDecimal(String raw, String column, int rowNumber) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("Valor vacío en " + column + " (fila " + rowNumber + ")");
        }
        try {
            return new BigDecimal(raw.replace(',', '.')).setScale(1, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw new BadRequestException("Valor numérico inválido en " + column + " (fila " + rowNumber + "): " + raw);
        }
    }

    private Integer parseCourseHandicap(String raw, int rowNumber) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("Valor vacío en HCO_COURSE_100 (fila " + rowNumber + ")");
        }
        try {
            return new BigDecimal(raw.replace(',', '.')).setScale(0, RoundingMode.HALF_UP).intValue();
        } catch (NumberFormatException e) {
            throw new BadRequestException("Valor numérico inválido en HCO_COURSE_100 (fila " + rowNumber + "): " + raw);
        }
    }

    private HandicapConversionDTO toDto(HandicapConversion conversion) {
        return HandicapConversionDTO.builder()
                .id(conversion.getId())
                .hcpIndexFrom(conversion.getHcpIndexFrom())
                .hcpIndexTo(conversion.getHcpIndexTo())
                .courseHandicap(conversion.getCourseHandicap())
                .build();
    }

    private record ParsedRow(BigDecimal from, BigDecimal to, Integer courseHandicap) {}

    private static final class FileTeeData {
        final String nombre;
        final String genero;
        final List<ParsedRow> rows;

        FileTeeData(String nombre, String genero, List<ParsedRow> rows) {
            this.nombre = nombre;
            this.genero = genero;
            this.rows = rows;
        }
    }
}
