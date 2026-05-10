package com.golf.tournament.service;

import com.golf.tournament.dto.inscription.InscriptionResponse;
import com.golf.tournament.dto.leaderboard.LeaderboardEntryDTO;
import com.golf.tournament.dto.leaderboard.TournamentScoreDTO;
import com.golf.tournament.dto.tournament.TournamentCategoryDTO;
import com.golf.tournament.dto.tournament.TournamentDTO;
import com.golf.tournament.dto.tournamentadmin.*;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExcelExportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final TournamentService tournamentService;
    private final InscriptionService inscriptionService;
    private final TournamentAdminService tournamentAdminService;
    private final LeaderboardService leaderboardService;
    private final FrutalesScoreService frutalesScoreService;
    private final ClasicScoreService clasicScoreService;
    private final TournamentAdminStageService stageService;
    private final TournamentAdminPlayoffResultService playoffResultService;

    // ─── Export 1: Inscriptos a un torneo (fecha) ─────────────────────────────

    public byte[] exportTournamentInscriptions(Long tournamentId) {
        TournamentDTO tournament = tournamentService.getTournamentById(tournamentId);
        List<InscriptionResponse> inscriptions = inscriptionService.getTournamentInscriptions(tournamentId);

        // HCP Course real viene del scorecard a través del leaderboard
        Map<Long, BigDecimal> hcpCourseByPlayerId = leaderboardService.getLeaderboard(tournamentId, null).stream()
                .filter(e -> e.getHandicapCourse() != null)
                .collect(Collectors.toMap(
                        LeaderboardEntryDTO::getPlayerId,
                        LeaderboardEntryDTO::getHandicapCourse,
                        (a, b) -> a));

        try (Workbook workbook = new XSSFWorkbook()) {
            ExcelStyles styles = new ExcelStyles(workbook);
            Sheet sheet = workbook.createSheet("Inscriptos");

            int rowIdx = 0;
            String[] headers = {"Jugador", "Matrícula", "HCP I.", "HCP C.", "Club"};
            createMergedTitleRow(sheet, "Inscriptos Torneo: " + tournament.getNombre(), rowIdx++, headers.length, styles.title);
            createInfoRow(sheet, "Total de jugadores: " + inscriptions.size(), rowIdx++, styles.info);
            rowIdx++; // fila vacía
            createHeaderRow(sheet, headers, rowIdx++, styles.header);

            for (InscriptionResponse insc : inscriptions) {
                Row row = sheet.createRow(rowIdx++);
                String jugador = insc.getPlayer().getApellido() + " " + insc.getPlayer().getNombre();
                BigDecimal hcpCourse = hcpCourseByPlayerId.getOrDefault(
                        insc.getPlayer().getId(),
                        insc.getHandicapCourse());
                setCell(row, 0, jugador, styles.data);
                setCell(row, 1, insc.getPlayer().getMatricula(), styles.data);
                setCell(row, 2, formatDecimal(insc.getPlayer().getHandicapIndex()), styles.data);
                setCell(row, 3, formatDecimal(hcpCourse), styles.data);
                setCell(row, 4, orDash(insc.getPlayer().getClubOrigen()), styles.data);
            }

            autoSizeColumns(sheet, headers.length);
            return workbookToBytes(workbook);
        } catch (IOException e) {
            throw new RuntimeException("Error generando Excel de inscriptos", e);
        }
    }

    // ─── Export 2: Inscriptos a un Torneo Administrativo ──────────────────────

    public byte[] exportAdminInscriptions(Long tournamentAdminId) {
        TournamentAdminDetailDTO detail = tournamentAdminService.getDetail(tournamentAdminId);

        try (Workbook workbook = new XSSFWorkbook()) {
            ExcelStyles styles = new ExcelStyles(workbook);
            Sheet sheet = workbook.createSheet("Inscriptos");

            int rowIdx = 0;
            String[] headers = {"Jugador", "Matrícula"};
            createMergedTitleRow(sheet, "Inscriptos Torneo: " + detail.getNombre(), rowIdx++, headers.length, styles.title);
            createInfoRow(sheet, "Total de jugadores: " + detail.getCurrentInscriptos(), rowIdx++, styles.info);
            rowIdx++; // fila vacía
            createHeaderRow(sheet, headers, rowIdx++, styles.header);

            for (TournamentAdminInscriptionDTO insc : detail.getInscriptions()) {
                Row row = sheet.createRow(rowIdx++);
                setCell(row, 0, insc.getPlayerName(), styles.data);
                setCell(row, 1, orDash(insc.getMatricula()), styles.data);
            }

            autoSizeColumns(sheet, headers.length);
            return workbookToBytes(workbook);
        } catch (IOException e) {
            throw new RuntimeException("Error generando Excel de inscriptos admin", e);
        }
    }

    // ─── Export 3: Resultados de un torneo (fecha) ────────────────────────────

    public byte[] exportTournamentResults(Long tournamentId) {
        TournamentDTO tournament = tournamentService.getTournamentById(tournamentId);
        List<LeaderboardEntryDTO> leaderboard = leaderboardService.getLeaderboard(tournamentId, null);

        boolean isClasic = "CLASICO".equals(tournament.getTipo()) && tournament.getScoringConfig() != null;
        boolean isFrutales = "FRUTALES".equals(tournament.getTipo()) && tournament.getScoringConfig() != null;

        List<TournamentScoreDTO> scores = List.of();
        if (isClasic) {
            scores = clasicScoreService.getScores(tournamentId);
        } else if (isFrutales) {
            scores = frutalesScoreService.getScores(tournamentId);
        }

        List<TournamentCategoryDTO> categories = sortedCategories(tournament);
        boolean hasCategories = !categories.isEmpty();

        String titleText = "Resultados Torneo: " + tournament.getNombre();
        String infoLine = buildTournamentInfoLine(tournament);
        String prizeLine = buildPrizeLine(tournament);

        try (Workbook workbook = new XSSFWorkbook()) {
            ExcelStyles styles = new ExcelStyles(workbook);

            if (isFrutales) {
                Map<Long, TournamentScoreDTO> globalScoreMap = scores.stream()
                        .filter(s -> "GLOBAL".equals(s.getScoreType()) || s.getScoreType() == null)
                        .collect(Collectors.toMap(TournamentScoreDTO::getPlayerId, s -> s, (a, b) -> a));

                // Jugadores con score ordenados por posición; sin score al final
                List<LeaderboardEntryDTO> sortedLeaderboard = leaderboard.stream()
                        .sorted(Comparator.comparingInt(e -> {
                            TournamentScoreDTO s = globalScoreMap.get(e.getPlayerId());
                            return (s != null && s.getPosition() != null) ? s.getPosition() : 9999;
                        }))
                        .collect(Collectors.toList());

                Sheet sheet = workbook.createSheet("Resultados");
                int rowIdx = writeResultHeader(sheet, titleText, infoLine, prizeLine, styles);
                writeFrutalesRows(sheet, sortedLeaderboard, globalScoreMap, tournament.getScoringConfig(), rowIdx, styles);

            } else if (isClasic) {
                Map<Long, Map<Long, TournamentScoreDTO>> categoryScoreMap = new HashMap<>();
                Map<Long, TournamentScoreDTO> scratchScoreMap = new HashMap<>();

                for (TournamentScoreDTO s : scores) {
                    if ("CATEGORY".equals(s.getScoreType()) && s.getCategoryId() != null) {
                        categoryScoreMap.computeIfAbsent(s.getCategoryId(), k -> new HashMap<>())
                                .put(s.getPlayerId(), s);
                    } else if ("SCRATCH".equals(s.getScoreType())) {
                        scratchScoreMap.put(s.getPlayerId(), s);
                    }
                }

                for (TournamentCategoryDTO cat : categories) {
                    String sheetName = sanitizeSheetName(cat.getNombre() + " (" + cat.getHandicapMin().intValue() + "-" + cat.getHandicapMax().intValue() + ")");
                    Sheet sheet = workbook.createSheet(sheetName);
                    int rowIdx = writeResultHeader(sheet, titleText, infoLine, prizeLine, styles);

                    Map<Long, TournamentScoreDTO> catScoreMap = categoryScoreMap.getOrDefault(cat.getId(), Map.of());
                    // Todos los jugadores de la categoría: con score primero (por posición), sin score al final
                    List<LeaderboardEntryDTO> catRows = leaderboard.stream()
                            .filter(e -> cat.getId() != null && cat.getId().equals(e.getCategoryId()))
                            .sorted(Comparator.comparingInt(e -> {
                                TournamentScoreDTO s = catScoreMap.get(e.getPlayerId());
                                return (s != null && s.getPosition() != null) ? s.getPosition() : 9999;
                            }))
                            .collect(Collectors.toList());
                    writeClasicRows(sheet, catRows, catScoreMap, tournament.getScoringConfig(), false, rowIdx, styles);
                }

                Sheet scratchSheet = workbook.createSheet("Scratch");
                int rowIdx = writeResultHeader(scratchSheet, titleText, infoLine, prizeLine, styles);
                List<LeaderboardEntryDTO> scratchRows;
                if (!scratchScoreMap.isEmpty()) {
                    // Con score por posición, sin score al final
                    scratchRows = leaderboard.stream()
                            .sorted(Comparator.comparingInt(e -> {
                                TournamentScoreDTO s = scratchScoreMap.get(e.getPlayerId());
                                return (s != null && s.getPosition() != null) ? s.getPosition() : 9999;
                            }))
                            .collect(Collectors.toList());
                } else {
                    // Sin scoring: con gross primero, sin gross al final
                    scratchRows = leaderboard.stream()
                            .sorted(Comparator.comparingInt(e -> e.getScoreGross() != null ? e.getScoreGross() : 9999))
                            .collect(Collectors.toList());
                }
                writeClasicRows(scratchSheet, scratchRows, scratchScoreMap, tournament.getScoringConfig(), true, rowIdx, styles);

            } else if (hasCategories) {
                Sheet generalSheet = workbook.createSheet("General");
                int rowIdx = writeResultHeader(generalSheet, titleText, infoLine, prizeLine, styles);
                // getLeaderboard() ya devuelve: con score (por scoreNeto) + sin score al final
                writeBasicRows(generalSheet, leaderboard, rowIdx, true, styles);

                for (TournamentCategoryDTO cat : categories) {
                    String sheetName = sanitizeSheetName(cat.getNombre() + " (" + cat.getHandicapMin().intValue() + "-" + cat.getHandicapMax().intValue() + ")");
                    Sheet catSheet = workbook.createSheet(sheetName);
                    rowIdx = writeResultHeader(catSheet, titleText, infoLine, prizeLine, styles);
                    List<LeaderboardEntryDTO> catRows = leaderboard.stream()
                            .filter(e -> cat.getId() != null && cat.getId().equals(e.getCategoryId()))
                            .collect(Collectors.toList());
                    writeBasicRows(catSheet, catRows, rowIdx, false, styles);
                }

                Sheet scratchSheet = workbook.createSheet("Scratch");
                rowIdx = writeResultHeader(scratchSheet, titleText, infoLine, prizeLine, styles);
                List<LeaderboardEntryDTO> scratchRows = leaderboard.stream()
                        .sorted(Comparator.comparingInt(e -> e.getScoreGross() != null ? e.getScoreGross() : 9999))
                        .collect(Collectors.toList());
                writeBasicRows(scratchSheet, scratchRows, rowIdx, false, styles);

            } else {
                Sheet sheet = workbook.createSheet("Resultados");
                int rowIdx = writeResultHeader(sheet, titleText, infoLine, prizeLine, styles);
                // getLeaderboard() incluye todos: con score primero, sin score al final
                writeBasicRows(sheet, leaderboard, rowIdx, false, styles);
            }

            return workbookToBytes(workbook);
        } catch (IOException e) {
            throw new RuntimeException("Error generando Excel de resultados", e);
        }
    }

    // ─── Export 4: Resultados de una etapa ───────────────────────────────────

    public byte[] exportStageBoard(Long tournamentAdminId, Long stageId) {
        TournamentAdminStageBoardDTO board = stageService.getStageBoard(tournamentAdminId, stageId);

        try (Workbook workbook = new XSSFWorkbook()) {
            ExcelStyles styles = new ExcelStyles(workbook);
            boolean isClasic = "CLASICO".equals(board.getTipo());

            if (isClasic && board.getCategoryRows() != null && !board.getCategoryRows().isEmpty()) {
                for (TournamentAdminStageBoardDTO.CategoryRowsDTO cat : board.getCategoryRows()) {
                    Sheet sheet = workbook.createSheet(sanitizeSheetName(cat.getCategoryName()));
                    writeStageBoardSheet(sheet, board, cat.getRows(), styles);
                }
                Sheet scratchSheet = workbook.createSheet("Scratch");
                writeStageBoardSheet(scratchSheet, board, board.getScratchRows() != null ? board.getScratchRows() : List.of(), styles);
            } else {
                Sheet sheet = workbook.createSheet("Resultados");
                writeStageBoardSheet(sheet, board, board.getRows(), styles);
            }

            return workbookToBytes(workbook);
        } catch (IOException e) {
            throw new RuntimeException("Error generando Excel de etapa", e);
        }
    }

    // ─── Export 5: Resultados de playoff ─────────────────────────────────────

    public byte[] exportPlayoffResults(Long tournamentAdminId) {
        TournamentAdminPlayoffResultsDTO results = playoffResultService.getResults(tournamentAdminId);

        try (Workbook workbook = new XSSFWorkbook()) {
            ExcelStyles styles = new ExcelStyles(workbook);
            boolean isClasic = "CLASICO".equals(results.getTipo());

            if (isClasic) {
                Sheet hcpSheet = workbook.createSheet("Con HCP");
                writePlayoffSheet(hcpSheet, results, results.getRows(), styles, workbook);
                Sheet scratchSheet = workbook.createSheet("Sin HCP (Scratch)");
                writePlayoffSheet(scratchSheet, results, results.getScratchRows() != null ? results.getScratchRows() : List.of(), styles, workbook);
            } else {
                Sheet sheet = workbook.createSheet("Resultados");
                writePlayoffSheet(sheet, results, results.getRows(), styles, workbook);
            }

            return workbookToBytes(workbook);
        } catch (IOException e) {
            throw new RuntimeException("Error generando Excel de playoff", e);
        }
    }

    // ─── Helpers: escritura de contenido ─────────────────────────────────────

    private int writeResultHeader(Sheet sheet, String title, String infoLine, String prizeLine, ExcelStyles styles) {
        int rowIdx = 0;
        createMergedTitleRow(sheet, title, rowIdx++, 13, styles.title);
        createInfoRow(sheet, infoLine, rowIdx++, styles.info);
        if (prizeLine != null) {
            createInfoRow(sheet, prizeLine, rowIdx++, styles.info);
        }
        rowIdx++; // fila vacía antes de encabezados
        return rowIdx;
    }

    private void writeFrutalesRows(Sheet sheet, List<LeaderboardEntryDTO> leaderboard,
                                   Map<Long, TournamentScoreDTO> scoreMap,
                                   ScoringConfigDTO config, int startRow, ExcelStyles styles) {
        boolean hasBirdie = config != null && config.getBirdiePoints() != null && config.getBirdiePoints() > 0;
        boolean hasEagle  = config != null && config.getEaglePoints()  != null && config.getEaglePoints()  > 0;
        boolean hasAce    = config != null && config.getAcePoints()    != null && config.getAcePoints()    > 0;

        List<String> headers = new ArrayList<>(List.of("Pos", "Jugador", "Matrícula", "HCP Index", "HCP Course", "Gross", "Neto"));
        if (hasBirdie) headers.add("Birdie");
        if (hasEagle)  headers.add("Águila");
        if (hasAce)    headers.add("Ace");
        headers.add("Puntos");

        createHeaderRow(sheet, headers.toArray(new String[0]), startRow++, styles.header);

        for (LeaderboardEntryDTO entry : leaderboard) {
            TournamentScoreDTO score = scoreMap.get(entry.getPlayerId());
            Row row = sheet.createRow(startRow++);
            int col = 0;
            // Posición real del scoring de FRUTALES; sin score → "-"
            String pos = score != null && score.getPosition() != null ? String.valueOf(score.getPosition()) : "-";
            setCell(row, col++, pos, styles.data);
            setCell(row, col++, entry.getPlayerName(), styles.data);
            setCell(row, col++, entry.getMatricula(), styles.data);
            setCell(row, col++, formatDecimal(entry.getHandicapIndex()), styles.data);
            setCell(row, col++, formatDecimal(entry.getHandicapCourse()), styles.data);
            setCell(row, col++, entry.getScoreGross() != null ? String.valueOf(entry.getScoreGross()) : "-", styles.data);
            setCell(row, col++, entry.getScoreNeto() != null ? entry.getScoreNeto().toPlainString() : "-", styles.data);
            if (hasBirdie) setCell(row, col++, score != null && score.getBirdieCount() != null ? String.valueOf(score.getBirdieCount()) : "-", styles.data);
            if (hasEagle)  setCell(row, col++, score != null && score.getEagleCount()  != null ? String.valueOf(score.getEagleCount())  : "-", styles.data);
            if (hasAce)    setCell(row, col++, score != null && score.getAceCount()    != null ? String.valueOf(score.getAceCount())    : "-", styles.data);
            setCell(row, col, score != null && score.getTotalPoints() != null ? String.valueOf(score.getTotalPoints()) : "-", styles.data);
        }

        autoSizeColumns(sheet, headers.size());
    }

    // Nota: la columna "#" se omite intencionalmente en todos los exports de resultados

    private void writeClasicRows(Sheet sheet, List<LeaderboardEntryDTO> rows,
                                 Map<Long, TournamentScoreDTO> scoreMap,
                                 ScoringConfigDTO config, boolean isScratch,
                                 int startRow, ExcelStyles styles) {
        boolean hasScoringData = config != null && !scoreMap.isEmpty();
        boolean hasBirdie = hasScoringData && config.getBirdiePoints() != null && config.getBirdiePoints() > 0;
        boolean hasEagle  = hasScoringData && config.getEaglePoints()  != null && config.getEaglePoints()  > 0;
        boolean hasAce    = hasScoringData && config.getAcePoints()    != null && config.getAcePoints()    > 0;

        List<String> headers = new ArrayList<>(List.of("Pos", "Jugador", "Matrícula", "HCP I", "HCP C", "Gross", "Neto", "To Par"));
        if (hasBirdie) headers.add("Birdie");
        if (hasEagle)  headers.add("Águila");
        if (hasAce)    headers.add("Ace");
        if (hasScoringData) headers.add("Puntos");
        headers.add("Club");

        createHeaderRow(sheet, headers.toArray(new String[0]), startRow++, styles.header);

        for (LeaderboardEntryDTO entry : rows) {
            TournamentScoreDTO score = scoreMap.get(entry.getPlayerId());
            Row row = sheet.createRow(startRow++);
            int col = 0;

            // Posición real del scoring de CLÁSICO; sin score → "-"
            String pos = score != null && score.getPosition() != null ? String.valueOf(score.getPosition()) : "-";
            setCell(row, col++, pos, styles.data);
            setCell(row, col++, entry.getPlayerName(), styles.data);
            setCell(row, col++, entry.getMatricula(), styles.data);
            setCell(row, col++, formatDecimal(entry.getHandicapIndex()), styles.data);
            setCell(row, col++, formatDecimal(entry.getHandicapCourse()), styles.data);
            setCell(row, col++, entry.getScoreGross() != null ? String.valueOf(entry.getScoreGross()) : "-", styles.data);
            setCell(row, col++, entry.getScoreNeto() != null ? entry.getScoreNeto().toPlainString() : "-", styles.data);
            setCell(row, col++, formatToPar(entry, isScratch), styles.data);
            if (hasBirdie) setCell(row, col++, score != null && score.getBirdieCount() != null ? String.valueOf(score.getBirdieCount()) : "-", styles.data);
            if (hasEagle)  setCell(row, col++, score != null && score.getEagleCount()  != null ? String.valueOf(score.getEagleCount())  : "-", styles.data);
            if (hasAce)    setCell(row, col++, score != null && score.getAceCount()    != null ? String.valueOf(score.getAceCount())    : "-", styles.data);
            if (hasScoringData) setCell(row, col++, score != null && score.getTotalPoints() != null ? String.valueOf(score.getTotalPoints()) : "-", styles.data);
            setCell(row, col, orDash(entry.getClubOrigen()), styles.data);
        }

        autoSizeColumns(sheet, headers.size());
    }

    private void writeBasicRows(Sheet sheet, List<LeaderboardEntryDTO> rows, int startRow,
                                boolean showCategory, ExcelStyles styles) {
        List<String> headers = new ArrayList<>(List.of("Pos", "Jugador", "Matrícula", "HCP I", "HCP C", "Gross", "Neto", "To Par", "Club"));
        if (showCategory) headers.add("Categoría");

        createHeaderRow(sheet, headers.toArray(new String[0]), startRow++, styles.header);

        int posCounter = 1;
        for (LeaderboardEntryDTO entry : rows) {
            boolean hasScore = "DELIVERED".equals(entry.getStatus());
            Row row = sheet.createRow(startRow++);
            int col = 0;
            setCell(row, col++, hasScore ? String.valueOf(posCounter++) : "-", styles.data);
            setCell(row, col++, entry.getPlayerName(), styles.data);
            setCell(row, col++, entry.getMatricula(), styles.data);
            setCell(row, col++, formatDecimal(entry.getHandicapIndex()), styles.data);
            setCell(row, col++, formatDecimal(entry.getHandicapCourse()), styles.data);
            setCell(row, col++, entry.getScoreGross() != null ? String.valueOf(entry.getScoreGross()) : "-", styles.data);
            setCell(row, col++, entry.getScoreNeto() != null ? entry.getScoreNeto().toPlainString() : "-", styles.data);
            setCell(row, col++, formatToPar(entry, false), styles.data);
            setCell(row, col++, orDash(entry.getClubOrigen()), styles.data);
            if (showCategory) setCell(row, col, orDash(entry.getCategoryName()), styles.data);
        }

        autoSizeColumns(sheet, headers.size());
    }

    private void writeStageBoardSheet(Sheet sheet, TournamentAdminStageBoardDTO board,
                                      List<TournamentAdminStageBoardDTO.PlayerStageRowDTO> rows, ExcelStyles styles) {
        int numCols = 1 + board.getTournaments().size() + 2;
        int rowIdx = 0;

        createMergedTitleRow(sheet, "Resultados Etapa: " + board.getStageName(), rowIdx++, numCols, styles.title);
        createInfoRow(sheet, "Fechas: " + board.getTournaments().size() + "   Jugadores: " + rows.size(), rowIdx++, styles.info);
        rowIdx++; // fila vacía

        Row headerRow = sheet.createRow(rowIdx++);
        setCell(headerRow, 0, "Jugador", styles.header);
        int colIdx = 1;
        for (TournamentAdminStageBoardDTO.TournamentDateColumnDTO t : board.getTournaments()) {
            String label = "Fecha: " + (t.getFechaInicio() != null ? t.getFechaInicio().format(DATE_FMT) : "-");
            if (Boolean.TRUE.equals(t.getDoublePoints())) label += " (x2)";
            setCell(headerRow, colIdx++, label, styles.header);
        }
        setCell(headerRow, colIdx++, "Puntos", styles.header);
        setCell(headerRow, colIdx, "Posición", styles.header);

        for (TournamentAdminStageBoardDTO.PlayerStageRowDTO rowData : rows) {
            Row dataRow = sheet.createRow(rowIdx++);
            setCell(dataRow, 0, rowData.getPlayerName(), styles.data);
            colIdx = 1;
            for (TournamentAdminStageBoardDTO.TournamentDateColumnDTO t : board.getTournaments()) {
                int pts = rowData.getPointsByTournament() != null
                        ? rowData.getPointsByTournament().getOrDefault(t.getTournamentId(), 0)
                        : 0;
                setCell(dataRow, colIdx++, String.valueOf(pts), styles.data);
            }
            setCell(dataRow, colIdx++, rowData.getTotalPoints() != null ? String.valueOf(rowData.getTotalPoints()) : "0", styles.data);
            setCell(dataRow, colIdx, rowData.getPosition() != null ? String.valueOf(rowData.getPosition()) : "-", styles.data);
        }

        autoSizeColumns(sheet, numCols);
    }

    private void writePlayoffSheet(Sheet sheet, TournamentAdminPlayoffResultsDTO results,
                                   List<TournamentAdminPlayoffResultsDTO.RowDTO> rows,
                                   ExcelStyles styles, Workbook workbook) {
        int numCols = 1 + results.getStages().size() + 2;
        long qualifiedCount = rows.stream().filter(r -> Boolean.TRUE.equals(r.getQualified())).count();
        int rowIdx = 0;

        createMergedTitleRow(sheet, "Resultados Play Off", rowIdx++, numCols, styles.title);
        createInfoRow(sheet, "Etapas: " + results.getStages().size() + "   Jugadores: " + rows.size() + "   Clasificados: " + qualifiedCount, rowIdx++, styles.info);
        rowIdx++; // fila vacía

        Row headerRow = sheet.createRow(rowIdx++);
        setCell(headerRow, 0, "Jugador", styles.header);
        int colIdx = 1;
        for (TournamentAdminPlayoffResultsDTO.StageColumnDTO stage : results.getStages()) {
            setCell(headerRow, colIdx++, stage.getCode(), styles.header);
        }
        setCell(headerRow, colIdx++, "Ptos", styles.header);
        setCell(headerRow, colIdx, "Pos", styles.header);

        CellStyle qualifiedStyle = buildQualifiedStyle(workbook, styles.data);

        for (TournamentAdminPlayoffResultsDTO.RowDTO rowData : rows) {
            Row dataRow = sheet.createRow(rowIdx++);
            setCell(dataRow, 0, rowData.getPlayerName(), styles.data);
            colIdx = 1;
            for (TournamentAdminPlayoffResultsDTO.StageColumnDTO stage : results.getStages()) {
                int pts = rowData.getPointsByStage() != null
                        ? rowData.getPointsByStage().getOrDefault(stage.getStageId(), 0)
                        : 0;
                setCell(dataRow, colIdx++, String.valueOf(pts), styles.data);
            }
            setCell(dataRow, colIdx++, rowData.getTotalPoints() != null ? String.valueOf(rowData.getTotalPoints()) : "0", styles.data);
            CellStyle posStyle = Boolean.TRUE.equals(rowData.getQualified()) ? qualifiedStyle : styles.data;
            setCell(dataRow, colIdx, rowData.getPosition() != null ? String.valueOf(rowData.getPosition()) : "-", posStyle);
        }

        autoSizeColumns(sheet, numCols);
    }

    // ─── POI helpers ─────────────────────────────────────────────────────────

    private void createMergedTitleRow(Sheet sheet, String text, int rowIdx, int numCols, CellStyle style) {
        Row row = sheet.createRow(rowIdx);
        row.setHeight((short) 600);
        Cell cell = row.createCell(0);
        cell.setCellValue(text);
        cell.setCellStyle(style);
        if (numCols > 1) {
            sheet.addMergedRegion(new CellRangeAddress(rowIdx, rowIdx, 0, numCols - 1));
        }
    }

    private void createInfoRow(Sheet sheet, String text, int rowIdx, CellStyle style) {
        Row row = sheet.createRow(rowIdx);
        Cell cell = row.createCell(0);
        cell.setCellValue(text);
        cell.setCellStyle(style);
    }

    private void createHeaderRow(Sheet sheet, String[] headers, int rowIdx, CellStyle style) {
        Row row = sheet.createRow(rowIdx);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
    }

    private void setCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "-");
        cell.setCellStyle(style);
    }

    private void autoSizeColumns(Sheet sheet, int numCols) {
        for (int i = 0; i < numCols; i++) {
            sheet.autoSizeColumn(i);
            int currentWidth = sheet.getColumnWidth(i);
            sheet.setColumnWidth(i, Math.min(currentWidth + 1024, 20000));
        }
    }

    private CellStyle buildQualifiedStyle(Workbook workbook, CellStyle baseStyle) {
        CellStyle style = workbook.createCellStyle();
        style.cloneStyleFrom(baseStyle);
        style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font boldFont = workbook.createFont();
        boldFont.setBold(true);
        style.setFont(boldFont);
        return style;
    }

    private byte[] workbookToBytes(Workbook workbook) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        return out.toByteArray();
    }

    // ─── Formatters ──────────────────────────────────────────────────────────

    private String formatDecimal(BigDecimal value) {
        if (value == null) return "-";
        return value.setScale(1, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatToPar(LeaderboardEntryDTO entry, boolean isScratch) {
        Integer toParVal = null;
        if (isScratch && entry.getScoreGross() != null
                && entry.getTotalPar() != null && entry.getTotalPar() > 0) {
            toParVal = entry.getScoreGross() - entry.getTotalPar();
        } else if (entry.getScoreToPar() != null) {
            toParVal = entry.getScoreToPar().intValue();
        }
        if (toParVal == null) return "-";
        if (toParVal == 0) return "E";
        return toParVal > 0 ? "+" + toParVal : String.valueOf(toParVal);
    }

    private String orDash(String value) {
        return value != null && !value.isBlank() ? value : "-";
    }

    private List<TournamentCategoryDTO> sortedCategories(TournamentDTO tournament) {
        if (tournament.getCategories() == null) return List.of();
        return tournament.getCategories().stream()
                .sorted(Comparator.comparingDouble(c -> c.getHandicapMin().doubleValue()))
                .collect(Collectors.toList());
    }

    private String buildTournamentInfoLine(TournamentDTO t) {
        String estado = switch (t.getEstado()) {
            case "IN_PROGRESS" -> "En Proceso";
            case "FINALIZED"   -> "Finalizado";
            default            -> "Pendiente";
        };
        String fechaStr = t.getFechaInicio() != null ? t.getFechaInicio().format(DATE_FMT) : "-";
        return "Campo: " + t.getCourseName() + "   Fecha: " + fechaStr + "   Estado: " + estado + "   Jugadores: " + t.getCurrentInscriptos();
    }

    private String buildPrizeLine(TournamentDTO t) {
        if (t.getPrizes() == null || t.getPrizes().isEmpty()) return null;
        Map<String, String> labels = Map.of(
                "LONG_DRIVER",   "Long Driver",
                "BEST_DRIVER",   "Best Driver",
                "BEST_APPROACH", "Best Approach"
        );
        String line = t.getPrizes().stream()
                .filter(p -> p.getWinnerName() != null)
                .map(p -> labels.getOrDefault(p.getPrizeType(), p.getPrizeType()) + ": " + p.getWinnerName())
                .collect(Collectors.joining("   "));
        return line.isEmpty() ? null : line;
    }

    public String slugify(String text) {
        if (text == null) return "export";
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        String noAccents = normalized.replaceAll("\\p{M}", "");
        return noAccents.toLowerCase()
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    public String sanitizeSheetName(String name) {
        if (name == null) return "Hoja";
        String clean = name.replaceAll("[\\[\\]*?:/\\\\]", "");
        return clean.substring(0, Math.min(clean.length(), 31));
    }

    // ─── Estilos ─────────────────────────────────────────────────────────────

    private static class ExcelStyles {
        final CellStyle title;
        final CellStyle info;
        final CellStyle header;
        final CellStyle data;

        ExcelStyles(Workbook workbook) {
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 20);

            title = workbook.createCellStyle();
            title.setFont(titleFont);
            title.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            title.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            title.setAlignment(HorizontalAlignment.LEFT);
            title.setVerticalAlignment(VerticalAlignment.CENTER);

            Font infoFont = workbook.createFont();
            infoFont.setItalic(true);
            infoFont.setFontHeightInPoints((short) 14);

            info = workbook.createCellStyle();
            info.setFont(infoFont);

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 14);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            header = workbook.createCellStyle();
            header.setFont(headerFont);
            header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setAlignment(HorizontalAlignment.CENTER);
            header.setVerticalAlignment(VerticalAlignment.CENTER);

            Font dataFont = workbook.createFont();
            dataFont.setFontHeightInPoints((short) 14);

            data = workbook.createCellStyle();
            data.setFont(dataFont);
            data.setVerticalAlignment(VerticalAlignment.CENTER);
            data.setBorderBottom(BorderStyle.THIN);
            data.setBorderTop(BorderStyle.THIN);
            data.setBorderLeft(BorderStyle.THIN);
            data.setBorderRight(BorderStyle.THIN);
            data.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            data.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            data.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            data.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        }
    }
}
