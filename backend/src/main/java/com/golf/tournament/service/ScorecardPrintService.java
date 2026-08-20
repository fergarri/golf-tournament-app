package com.golf.tournament.service;

import com.golf.tournament.exception.BadRequestException;
import com.golf.tournament.exception.ResourceNotFoundException;
import com.golf.tournament.model.CourseTee;
import com.golf.tournament.model.Hole;
import com.golf.tournament.model.HoleDistance;
import com.golf.tournament.model.Player;
import com.golf.tournament.model.Tournament;
import com.golf.tournament.model.TournamentCategory;
import com.golf.tournament.repository.HandicapConversionRepository;
import com.golf.tournament.repository.HoleRepository;
import com.golf.tournament.repository.PlayerRepository;
import com.golf.tournament.repository.TournamentCategoryRepository;
import com.golf.tournament.repository.TournamentRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Genera un PDF con tarjetas de golf imprimibles, en blanco (sin golpes cargados), para
 * uno o varios jugadores de un torneo. Pensado para poder imprimirlas en papel o enviarlas
 * a una imprenta, con varias tarjetas por hoja A4.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScorecardPrintService {

    private static final float MM_TO_PT = 72f / 25.4f;
    private static final float OUTER_MARGIN_MM = 10f;
    private static final float CARD_GAP_MM = 8f;
    private static final float FOLD_GAP_MM = 3f;
    private static final float FIXED_SCORE_ROW_MM = 7f;
    private static final float CUT_GAP_MM = 3f;

    private static final String SEX_MALE = "M";
    private static final String SEX_FEMALE = "F";
    private static final String SEX_MIXED = "X";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final TournamentRepository tournamentRepository;
    private final PlayerRepository playerRepository;
    private final HoleRepository holeRepository;
    private final TournamentCategoryRepository categoryRepository;
    private final HandicapConversionRepository handicapConversionRepository;

    @Transactional(readOnly = true)
    public byte[] generatePrintablePdf(Long tournamentId, List<Long> playerIds) {
        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", "id", tournamentId));

        Map<Long, Player> playersById = playerRepository.findAllById(playerIds).stream()
                .collect(Collectors.toMap(Player::getId, p -> p));

        List<Player> orderedPlayers = playerIds.stream()
                .distinct()
                .map(playersById::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (orderedPlayers.isEmpty()) {
            throw new BadRequestException("No se encontraron jugadores válidos para imprimir");
        }

        List<Hole> allHoles = holeRepository.findByCourseIdOrderByNumeroHoyoAsc(tournament.getCourse().getId());
        List<TournamentCategory> categories = categoryRepository.findByTournamentId(tournamentId);

        int cantidadHoyos = (tournament.getCantidadHoyosJuego() != null && tournament.getCantidadHoyosJuego() == 9)
                ? 9 : 18;

        List<Hole> holesToPrint = allHoles.stream()
                .filter(hole -> cantidadHoyos == 18 || hole.getNumeroHoyo() <= 9)
                .sorted(Comparator.comparing(Hole::getNumeroHoyo))
                .collect(Collectors.toList());

        List<PlayerCardData> cards = orderedPlayers.stream()
                .map(player -> buildCardData(tournament, player, holesToPrint, categories, cantidadHoyos))
                .collect(Collectors.toList());

        boolean landscape = cantidadHoyos == 18;
        int cardsPerPage = landscape ? 3 : 4;
        PDRectangle pageSize = landscape
                ? new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth())
                : PDRectangle.A4;

        try (PDDocument document = new PDDocument()) {
            PDFont regularFont = PDType1Font.HELVETICA;
            PDFont boldFont = PDType1Font.HELVETICA_BOLD;

            for (int i = 0; i < cards.size(); i += cardsPerPage) {
                List<PlayerCardData> pageCards = cards.subList(i, Math.min(i + cardsPerPage, cards.size()));
                drawPage(document, pageSize, pageCards, cardsPerPage, regularFont, boldFont);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Error generando el PDF de tarjetas", e);
        }
    }

    // ------------------------------------------------------------------
    // Construcción de datos por jugador
    // ------------------------------------------------------------------

    private PlayerCardData buildCardData(Tournament tournament,
                                          Player player,
                                          List<Hole> holes,
                                          List<TournamentCategory> categories,
                                          int cantidadHoyos) {
        String sexo = player.getSexo() != null ? player.getSexo().trim().toUpperCase() : null;
        CourseTee tee = SEX_FEMALE.equals(sexo) ? tournament.getTeeFemenino() : tournament.getTeeMasculino();

        BigDecimal handicapCourse = null;
        if (tee != null && player.getHandicapIndex() != null) {
            handicapCourse = handicapConversionRepository
                    .findByTeeAndHandicapIndex(tee.getId(), player.getHandicapIndex())
                    .map(conversion -> {
                        double courseHandicap = cantidadHoyos == 9
                                ? conversion.getCourseHandicap() / 2.0
                                : conversion.getCourseHandicap();
                        return BigDecimal.valueOf(courseHandicap).setScale(1, RoundingMode.HALF_UP);
                    })
                    .orElse(null);
        }

        TournamentCategory category = findCategoryForHandicap(player.getHandicapIndex(), sexo, categories);
        Long teeId = tee != null ? tee.getId() : null;

        List<HoleRow> holeRows = holes.stream()
                .map(hole -> new HoleRow(
                        hole.getNumeroHoyo(),
                        hole.getPar(),
                        hole.getHandicap(),
                        teeId != null ? distanceForTee(hole, teeId) : null))
                .collect(Collectors.toList());

        return PlayerCardData.builder()
                .courseName(tournament.getCourse() != null ? tournament.getCourse().getNombre() : null)
                .playerName((player.getNombre() + " " + player.getApellido()).trim())
                .playerInitials(buildInitials(player.getNombre(), player.getApellido()))
                .matricula(player.getMatricula())
                .handicapIndex(player.getHandicapIndex())
                .handicapCourse(handicapCourse)
                .categoryName(category != null ? category.getNombre() : null)
                .teeName(tee != null ? tee.getNombre() : null)
                .fecha(tournament.getFechaInicio() != null ? tournament.getFechaInicio().format(DATE_FORMAT) : null)
                .cantidadHoyos(cantidadHoyos)
                .holes(holeRows)
                .build();
    }

    private String buildInitials(String nombre, String apellido) {
        String n = firstLetter(nombre);
        String a = firstLetter(apellido);
        StringBuilder sb = new StringBuilder();
        if (!n.isEmpty()) {
            sb.append(n).append(".");
        }
        if (!a.isEmpty()) {
            sb.append(a).append(".");
        }
        return sb.length() > 0 ? sb.toString() : "-";
    }

    private String firstLetter(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? "" : String.valueOf(Character.toUpperCase(trimmed.charAt(0)));
    }

    private Integer distanceForTee(Hole hole, Long teeId) {
        if (hole.getDistances() == null) {
            return null;
        }
        return hole.getDistances().stream()
                .filter(d -> d.getCourseTee() != null && Objects.equals(d.getCourseTee().getId(), teeId))
                .map(HoleDistance::getDistanciaYardas)
                .findFirst()
                .orElse(null);
    }

    private TournamentCategory findCategoryForHandicap(BigDecimal handicapIndex,
                                                        String playerSex,
                                                        List<TournamentCategory> categories) {
        if (handicapIndex == null || categories == null || categories.isEmpty()) {
            return null;
        }
        String normalizedPlayerSex = normalizeSex(playerSex);
        for (TournamentCategory category : categories) {
            String categorySex = normalizeSex(category.getSexoCategoria());
            boolean applies = SEX_MIXED.equals(categorySex) || categorySex.equals(normalizedPlayerSex);
            if (!applies) {
                continue;
            }
            if (handicapIndex.compareTo(category.getHandicapMin()) >= 0
                    && handicapIndex.compareTo(category.getHandicapMax()) <= 0) {
                return category;
            }
        }
        return null;
    }

    private String normalizeSex(String sex) {
        if (sex == null || sex.isBlank()) {
            return SEX_MIXED;
        }
        String normalized = sex.trim().toUpperCase();
        if (SEX_MALE.equals(normalized) || SEX_FEMALE.equals(normalized)) {
            return normalized;
        }
        return SEX_MIXED;
    }

    // ------------------------------------------------------------------
    // Dibujo del PDF
    // ------------------------------------------------------------------

    private void drawPage(PDDocument document,
                           PDRectangle pageSize,
                           List<PlayerCardData> pageCards,
                           int cardsPerPage,
                           PDFont regularFont,
                           PDFont boldFont) throws IOException {
        PDPage page = new PDPage(pageSize);
        document.addPage(page);

        float marginPt = OUTER_MARGIN_MM * MM_TO_PT;
        float gapPt = CARD_GAP_MM * MM_TO_PT;
        float pageWidth = pageSize.getWidth();
        float pageHeight = pageSize.getHeight();

        float cardWidth = pageWidth - 2 * marginPt;
        float usableHeight = pageHeight - 2 * marginPt - (cardsPerPage - 1) * gapPt;
        float cardHeight = usableHeight / cardsPerPage;

        try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
            cs.setStrokingColor(java.awt.Color.BLACK);
            cs.setNonStrokingColor(java.awt.Color.BLACK);

            for (int slot = 0; slot < cardsPerPage; slot++) {
                float cardTopY = pageHeight - marginPt - slot * (cardHeight + gapPt);
                float cardBottomY = cardTopY - cardHeight;

                if (slot < pageCards.size()) {
                    drawCard(cs, marginPt, cardBottomY, cardWidth, cardHeight, pageCards.get(slot), regularFont, boldFont);
                }

                if (slot < cardsPerPage - 1) {
                    float cutY = cardBottomY - gapPt / 2f;
                    drawCutLine(cs, marginPt, pageWidth - marginPt, cutY);
                }
            }
        }
    }

    private void drawCutLine(PDPageContentStream cs, float x1, float x2, float y) throws IOException {
        cs.setLineDashPattern(new float[]{4f, 3f}, 0);
        cs.setLineWidth(0.5f);
        cs.moveTo(x1, y);
        cs.lineTo(x2, y);
        cs.stroke();
        cs.setLineDashPattern(new float[]{}, 0);
    }

    private void drawCard(PDPageContentStream cs,
                           float x, float y, float width, float height,
                           PlayerCardData data,
                           PDFont regularFont, PDFont boldFont) throws IOException {
        boolean hasFold = data.getCantidadHoyos() == 18;
        List<Column> columns = buildColumns(data.getCantidadHoyos());
        Map<Integer, HoleRow> holesByNumber = data.getHoles().stream()
                .collect(Collectors.toMap(HoleRow::getNumeroHoyo, h -> h));

        int foldAfterIndex = -1;
        if (hasFold) {
            for (int i = 0; i < columns.size(); i++) {
                if (columns.get(i).type == ColumnType.IDA_SUBTOTAL) {
                    foldAfterIndex = i;
                    break;
                }
            }
        }
        float foldGapPt = hasFold ? FOLD_GAP_MM * MM_TO_PT : 0f;

        float totalWeight = 0f;
        for (Column c : columns) {
            totalWeight += c.weight;
        }
        float availableWidth = width - foldGapPt;
        float[] colX = new float[columns.size() + 1];
        colX[0] = x;
        for (int i = 0; i < columns.size(); i++) {
            colX[i + 1] = colX[i] + availableWidth * (columns.get(i).weight / totalWeight);
            if (i == foldAfterIndex) {
                colX[i + 1] += foldGapPt;
            }
        }
        colX[columns.size()] = x + width;

        float leftHalfEndX = foldAfterIndex >= 0 ? colX[foldAfterIndex + 1] - foldGapPt : x + width;
        float rightHalfStartX = foldAfterIndex >= 0 ? colX[foldAfterIndex + 1] : x + width;

        // Bandas: [0] campo, [1] jugador, [2] datos/firmas, [3] HOYO, [4] TEE, [5] HANDICAP,
        // [6] PAR, [7] golpes jugador (fija), [8] espacio de corte, [9] golpes marcador (fija)
        float fixedRowPt = FIXED_SCORE_ROW_MM * MM_TO_PT;
        float cutGapPt = CUT_GAP_MM * MM_TO_PT;
        float[] flexWeights = {0.9f, 0.8f, 1.4f, 1f, 1f, 1f, 1f};
        float flexTotalWeight = 0f;
        for (float w : flexWeights) {
            flexTotalWeight += w;
        }
        float flexHeight = height - 2 * fixedRowPt - cutGapPt;

        float[] rowY = new float[11];
        rowY[0] = y + height;
        for (int i = 0; i < flexWeights.length; i++) {
            rowY[i + 1] = rowY[i] - flexHeight * (flexWeights[i] / flexTotalWeight);
        }
        rowY[8] = rowY[7] - fixedRowPt;
        rowY[9] = rowY[8] - cutGapPt;
        rowY[10] = y;

        // Bloque principal (campo, jugador, datos, grilla de hoyos y fila del jugador)
        cs.setLineWidth(0.9f);
        cs.addRect(x, rowY[8], width, rowY[0] - rowY[8]);
        cs.stroke();

        // Bloque de la fila del marcador: separado del bloque principal, unidos solo por la
        // línea punteada de corte en el espacio en blanco entre ambos
        cs.addRect(x, y, width, rowY[9] - y);
        cs.stroke();

        // Separadores horizontales completos: cierre de banda "campo" y de banda "jugador"
        cs.setLineWidth(0.6f);
        cs.moveTo(x, rowY[1]);
        cs.lineTo(x + width, rowY[1]);
        cs.stroke();
        cs.moveTo(x, rowY[2]);
        cs.lineTo(x + width, rowY[2]);
        cs.stroke();

        // Separadores horizontales de la grilla de hoyos, con corte en el espacio de doblado
        for (int i = 3; i <= 7; i++) {
            drawHorizontalRule(cs, rowY[i], x, x + width,
                    hasFold ? leftHalfEndX : -1f, hasFold ? rightHalfStartX : -1f);
        }

        // Separadores verticales de la grilla de hoyos (se omite la banda 8, espacio de corte en blanco)
        cs.setLineWidth(0.35f);
        int[] gridRowIndices = {3, 4, 5, 6, 7, 9};
        for (int rowIdx : gridRowIndices) {
            float top = rowY[rowIdx];
            float bottom = rowY[rowIdx + 1];
            for (int c = 1; c < columns.size(); c++) {
                cs.moveTo(colX[c], top);
                cs.lineTo(colX[c], bottom);
                cs.stroke();
            }
        }

        // Línea punteada de corte entre la fila del jugador y la del marcador
        float cutLineY = (rowY[8] + rowY[9]) / 2f;
        cs.setLineDashPattern(new float[]{4f, 3f}, 0);
        cs.setLineWidth(0.5f);
        cs.moveTo(x, cutLineY);
        cs.lineTo(x + width, cutLineY);
        cs.stroke();
        cs.setLineDashPattern(new float[]{}, 0);

        if (hasFold) {
            cs.setLineWidth(0.5f);
            cs.moveTo(leftHalfEndX, rowY[2]);
            cs.lineTo(leftHalfEndX, rowY[8]);
            cs.stroke();
            cs.moveTo(rightHalfStartX, rowY[2]);
            cs.lineTo(rightHalfStartX, rowY[8]);
            cs.stroke();

            cs.moveTo(leftHalfEndX, rowY[9]);
            cs.lineTo(leftHalfEndX, rowY[10]);
            cs.stroke();
            cs.moveTo(rightHalfStartX, rowY[9]);
            cs.lineTo(rightHalfStartX, rowY[10]);
            cs.stroke();
        }

        drawLeftText(cs, boldFont, 10.5f, data.getCourseName() != null ? data.getCourseName().toUpperCase() : "-",
                x, width, rowY[1], rowY[0] - rowY[1], 5f);

        drawLeftText(cs, boldFont, 8f, "Jugador: " + data.getPlayerName(),
                x, width, rowY[2], rowY[1] - rowY[2], 5f);

        if (hasFold) {
            drawInfoSegments(cs, regularFont, boldFont, x, rowY[2], leftHalfEndX - x, rowY[2] - rowY[3], data);
            drawSignatureBoxes(cs, regularFont, rightHalfStartX, rowY[2], (x + width) - rightHalfStartX, rowY[2] - rowY[3]);
        } else {
            drawInfoSegments(cs, regularFont, boldFont, x, rowY[2], width, rowY[2] - rowY[3], data);
        }

        drawRowLabelAndCells(cs, boldFont, 7.2f, columns, colX, rowY[3], rowY[4],
                "HOYO", col -> headerCellText(col));

        String teeLabel = data.getTeeName() != null ? data.getTeeName().toUpperCase() : "SIN TEE DEFINIDO";
        drawRowLabelAndCells(cs, regularFont, 6.8f, columns, colX, rowY[4], rowY[5],
                teeLabel, col -> teeCellText(col, holesByNumber));

        drawRowLabelAndCells(cs, regularFont, 6.8f, columns, colX, rowY[5], rowY[6],
                "HANDICAP", col -> handicapCellText(col, holesByNumber));

        drawRowLabelAndCells(cs, boldFont, 6.8f, columns, colX, rowY[6], rowY[7],
                "PAR", col -> parCellText(col, holesByNumber));

        drawRowLabelAndCells(cs, boldFont, 6.6f, columns, colX, rowY[7], rowY[8],
                data.getPlayerInitials(), col -> "");

        drawRowLabelAndCells(cs, regularFont, 6.2f, columns, colX, rowY[9], rowY[10],
                "", col -> "");
        drawTopLeftText(cs, regularFont, 5.5f, "Marcador",
                colX[0], colX[1] - colX[0], rowY[9], rowY[10] - rowY[9], 2.5f);
    }

    private void drawHorizontalRule(PDPageContentStream cs, float rowYVal, float xStart, float xEnd,
                                     float gapStart, float gapEnd) throws IOException {
        cs.setLineWidth(0.35f);
        if (gapStart < 0) {
            cs.moveTo(xStart, rowYVal);
            cs.lineTo(xEnd, rowYVal);
            cs.stroke();
        } else {
            cs.moveTo(xStart, rowYVal);
            cs.lineTo(gapStart, rowYVal);
            cs.stroke();
            cs.moveTo(gapEnd, rowYVal);
            cs.lineTo(xEnd, rowYVal);
            cs.stroke();
        }
    }

    private List<Column> buildColumns(int cantidadHoyos) {
        List<Column> columns = new ArrayList<>();
        columns.add(new Column(ColumnType.LABEL, null, 2.6f));
        if (cantidadHoyos == 18) {
            for (int n = 1; n <= 9; n++) {
                columns.add(new Column(ColumnType.HOLE, n, 1f));
            }
            columns.add(new Column(ColumnType.IDA_SUBTOTAL, null, 1.5f));
            for (int n = 10; n <= 18; n++) {
                columns.add(new Column(ColumnType.HOLE, n, 1f));
            }
            columns.add(new Column(ColumnType.VUELTA_SUBTOTAL, null, 1.5f));
        } else {
            for (int n = 1; n <= 9; n++) {
                columns.add(new Column(ColumnType.HOLE, n, 1f));
            }
        }
        columns.add(new Column(ColumnType.TOTAL, null, 1.6f));
        columns.add(new Column(ColumnType.HCP, null, 1.2f));
        columns.add(new Column(ColumnType.NETO, null, 1.2f));
        return columns;
    }

    private String headerCellText(Column col) {
        switch (col.type) {
            case HOLE:
                return String.valueOf(col.numeroHoyo);
            case IDA_SUBTOTAL:
                return "IDA";
            case VUELTA_SUBTOTAL:
                return "VTA.";
            case TOTAL:
                return "TOTAL";
            case HCP:
                return "HCP I.";
            case NETO:
                return "NETO";
            default:
                return "";
        }
    }

    private String teeCellText(Column col, Map<Integer, HoleRow> holesByNumber) {
        switch (col.type) {
            case HOLE:
                return formatOrDash(holeValue(holesByNumber, col.numeroHoyo, HoleRow::getDistancia));
            case IDA_SUBTOTAL:
                return sumRange(holesByNumber, 1, 9, HoleRow::getDistancia);
            case VUELTA_SUBTOTAL:
                return sumRange(holesByNumber, 10, 18, HoleRow::getDistancia);
            case TOTAL:
                return sumRange(holesByNumber, 1, 18, HoleRow::getDistancia);
            default:
                return "";
        }
    }

    private String handicapCellText(Column col, Map<Integer, HoleRow> holesByNumber) {
        if (col.type == ColumnType.HOLE) {
            return formatOrDash(holeValue(holesByNumber, col.numeroHoyo, HoleRow::getHandicapHoyo));
        }
        return "";
    }

    private String parCellText(Column col, Map<Integer, HoleRow> holesByNumber) {
        switch (col.type) {
            case HOLE:
                return formatOrDash(holeValue(holesByNumber, col.numeroHoyo, HoleRow::getPar));
            case IDA_SUBTOTAL:
                return sumRange(holesByNumber, 1, 9, HoleRow::getPar);
            case VUELTA_SUBTOTAL:
                return sumRange(holesByNumber, 10, 18, HoleRow::getPar);
            case TOTAL:
                return sumRange(holesByNumber, 1, 18, HoleRow::getPar);
            default:
                return "";
        }
    }

    private Integer holeValue(Map<Integer, HoleRow> holesByNumber, Integer numero,
                               java.util.function.Function<HoleRow, Integer> extractor) {
        HoleRow row = holesByNumber.get(numero);
        return row != null ? extractor.apply(row) : null;
    }

    private String sumRange(Map<Integer, HoleRow> holesByNumber, int from, int to,
                             java.util.function.Function<HoleRow, Integer> extractor) {
        int sum = 0;
        boolean any = false;
        for (int n = from; n <= to; n++) {
            HoleRow row = holesByNumber.get(n);
            if (row == null) {
                continue;
            }
            Integer value = extractor.apply(row);
            if (value != null) {
                sum += value;
                any = true;
            }
        }
        return any ? String.valueOf(sum) : "-";
    }

    private String formatOrDash(Integer value) {
        return value != null ? String.valueOf(value) : "-";
    }

    private void drawRowLabelAndCells(PDPageContentStream cs,
                                       PDFont font,
                                       float fontSize,
                                       List<Column> columns,
                                       float[] colX,
                                       float topY,
                                       float bottomY,
                                       String labelText,
                                       CellTextProvider provider) throws IOException {
        float rowHeight = topY - bottomY;
        drawLeftText(cs, font, fontSize, labelText, colX[0], colX[1] - colX[0], bottomY, rowHeight, 4f);
        for (int i = 1; i < columns.size(); i++) {
            String text = provider.textFor(columns.get(i));
            drawCenteredText(cs, font, fontSize, text, colX[i], colX[i + 1] - colX[i], bottomY, rowHeight);
        }
    }

    private void drawInfoSegments(PDPageContentStream cs,
                                   PDFont regularFont,
                                   PDFont boldFont,
                                   float x, float topY, float width, float rowHeight,
                                   PlayerCardData data) throws IOException {
        String fecha = data.getFecha() != null ? data.getFecha() : "-";
        String matricula = data.getMatricula() != null ? data.getMatricula() : "-";
        String hcpIndex = formatDecimal(data.getHandicapIndex());
        String hcpCourse = formatDecimal(data.getHandicapCourse());
        String categoria = data.getCategoryName() != null ? data.getCategoryName() : "-";

        String[] labels = {"FECHA", "MATRICULA", "HCP INDEX", "HCP COURSE", "CATEGORIA"};
        String[] values = {fecha, matricula, hcpIndex, hcpCourse, categoria};

        float bottomY = topY - rowHeight;
        float segmentWidth = width / labels.length;

        for (int i = 0; i < labels.length; i++) {
            float segX = x + i * segmentWidth;
            if (i > 0) {
                cs.setLineWidth(0.35f);
                cs.moveTo(segX, bottomY);
                cs.lineTo(segX, topY);
                cs.stroke();
            }
            drawCenteredText(cs, regularFont, 6f, labels[i], segX, segmentWidth, bottomY + rowHeight * 0.55f, rowHeight * 0.4f);
            drawCenteredText(cs, boldFont, 8.5f, values[i], segX, segmentWidth, bottomY + rowHeight * 0.08f, rowHeight * 0.4f);
        }
    }

    private void drawSignatureBoxes(PDPageContentStream cs, PDFont font,
                                     float x, float topY, float width, float rowHeight) throws IOException {
        float inset = 1.2f * MM_TO_PT;
        float gapBetween = 2f * MM_TO_PT;
        float usableWidth = width - 2 * inset - gapBetween;
        float boxWidth = usableWidth / 2f;
        float boxHeight = rowHeight - 2 * inset;
        float boxY = topY - rowHeight + inset;

        float box1X = x + inset;
        float box2X = box1X + boxWidth + gapBetween;

        drawSignatureBox(cs, font, "Firma Jugador", box1X, boxY, boxWidth, boxHeight);
        drawSignatureBox(cs, font, "Firma Marcador", box2X, boxY, boxWidth, boxHeight);
    }

    private void drawSignatureBox(PDPageContentStream cs, PDFont font, String label,
                                   float x, float y, float width, float height) throws IOException {
        cs.setLineWidth(0.4f);
        cs.addRect(x, y, width, height);
        cs.stroke();
        float labelHeight = Math.min(height * 0.4f, 9f);
        drawLeftText(cs, font, 6f, label, x, width, y + height - labelHeight, labelHeight, 3f);
    }

    private void drawCenteredText(PDPageContentStream cs, PDFont font, float fontSize, String text,
                                   float cellX, float cellWidth, float cellY, float cellHeight) throws IOException {
        if (text == null || text.isEmpty()) {
            return;
        }
        float textWidth = font.getStringWidth(text) / 1000 * fontSize;
        float tx = cellX + Math.max(0, (cellWidth - textWidth) / 2f);
        float ty = cellY + (cellHeight - fontSize) / 2f + fontSize * 0.2f;
        cs.beginText();
        cs.setFont(font, fontSize);
        cs.newLineAtOffset(tx, ty);
        cs.showText(text);
        cs.endText();
    }

    private void drawLeftText(PDPageContentStream cs, PDFont font, float fontSize, String text,
                               float cellX, float cellWidth, float cellY, float cellHeight,
                               float paddingLeft) throws IOException {
        if (text == null || text.isEmpty()) {
            return;
        }
        float maxWidth = cellWidth - paddingLeft * 2;
        String fitted = fitTextToWidth(font, fontSize, text, maxWidth);
        float ty = cellY + (cellHeight - fontSize) / 2f + fontSize * 0.2f;
        cs.beginText();
        cs.setFont(font, fontSize);
        cs.newLineAtOffset(cellX + paddingLeft, ty);
        cs.showText(fitted);
        cs.endText();
    }

    private void drawTopLeftText(PDPageContentStream cs, PDFont font, float fontSize, String text,
                                  float cellX, float cellWidth, float cellY, float cellHeight,
                                  float padding) throws IOException {
        if (text == null || text.isEmpty()) {
            return;
        }
        float maxWidth = cellWidth - padding * 2;
        String fitted = fitTextToWidth(font, fontSize, text, maxWidth);
        float ty = cellY + cellHeight - fontSize - padding * 0.6f;
        cs.beginText();
        cs.setFont(font, fontSize);
        cs.newLineAtOffset(cellX + padding, ty);
        cs.showText(fitted);
        cs.endText();
    }

    private String fitTextToWidth(PDFont font, float fontSize, String text, float maxWidth) throws IOException {
        if (maxWidth <= 0 || font.getStringWidth(text) / 1000 * fontSize <= maxWidth) {
            return text;
        }
        String truncated = text;
        while (truncated.length() > 1
                && font.getStringWidth(truncated + "...") / 1000 * fontSize > maxWidth) {
            truncated = truncated.substring(0, truncated.length() - 1);
        }
        return truncated + "...";
    }

    private String formatDecimal(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        BigDecimal stripped = value.stripTrailingZeros();
        if (stripped.scale() < 0) {
            stripped = stripped.setScale(0);
        }
        return stripped.toPlainString();
    }

    @FunctionalInterface
    private interface CellTextProvider {
        String textFor(Column column) throws IOException;
    }

    private enum ColumnType { LABEL, HOLE, IDA_SUBTOTAL, VUELTA_SUBTOTAL, TOTAL, HCP, NETO }

    private static final class Column {
        final ColumnType type;
        final Integer numeroHoyo;
        final float weight;

        Column(ColumnType type, Integer numeroHoyo, float weight) {
            this.type = type;
            this.numeroHoyo = numeroHoyo;
            this.weight = weight;
        }
    }

    @Data
    @Builder
    private static class PlayerCardData {
        private String courseName;
        private String playerName;
        private String playerInitials;
        private String matricula;
        private BigDecimal handicapIndex;
        private BigDecimal handicapCourse;
        private String categoryName;
        private String teeName;
        private String fecha;
        private int cantidadHoyos;
        private List<HoleRow> holes;
    }

    @Data
    @AllArgsConstructor
    private static class HoleRow {
        private Integer numeroHoyo;
        private Integer par;
        private Integer handicapHoyo;
        private Integer distancia;
    }
}
