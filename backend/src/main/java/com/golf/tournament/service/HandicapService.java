package com.golf.tournament.service;

import com.golf.tournament.dto.handicap.HandicapInfoDTO;
import com.golf.tournament.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
public class HandicapService {

    @Value("${handicap.api.url}")
    private String handicapApiUrl;

    @Value("${handicap.api.timeout:10000}")
    private int timeout;

    public HandicapInfoDTO fetchHandicapInfo(String matricula) {
        try {
            log.info("Fetching handicap info for matricula: {}", matricula);

            Connection.Response response = Jsoup.connect(handicapApiUrl)
                    .method(Connection.Method.POST)
                    .data("TxtNroMatricula", matricula)
                    .data("TxtApellido", "")
                    .data("Enviar", "Aceptar")
                    .timeout(timeout)
                    .execute();

            Document doc = response.parse();

            Elements tables = doc.select("table");
            if (tables.isEmpty()) {
                log.warn("No table found in response for matricula: {}", matricula);
                log.debug("Response body: {}", doc.body().text());
                throw new BadRequestException("Player not found with matricula: " + matricula);
            }

            Element table = tables.first();
            Elements rows = table.select("tr");

            if (rows.size() < 2) {
                log.warn("Not enough rows in table for matricula: {}. Table has {} rows", 
                        matricula, rows.size());
                log.debug("Table HTML: {}", table.html());
                throw new BadRequestException("Player not found with matricula: " + matricula);
            }

            Element dataRow = null;
            for (int i = 1; i < rows.size(); i++) {
                Element row = rows.get(i);
                Elements cells = row.select("td");
                if (!cells.isEmpty()) {
                    dataRow = row;
                    break;
                }
            }

            if (dataRow == null) {
                throw new BadRequestException("Player not found with matricula: " + matricula);
            }

            Elements cells = dataRow.select("td");
            log.info("Found {} cells in data row for matricula: {}", cells.size(), matricula);
            
            // Log cell contents for debugging
            for (int i = 0; i < cells.size(); i++) {
                log.debug("Cell {}: {}", i, cells.get(i).text().trim());
            }
            
            if (cells.size() < 3) {
                log.warn("Not enough cells in data row for matricula: {}. Expected at least 3, got {}", 
                        matricula, cells.size());
                throw new BadRequestException("Player not found or invalid data format from handicap API");
            }

            String nombreCompleto = cells.get(0).text().trim();
            String handicapIndexStr = cells.size() > 2 ? cells.get(2).text().trim() : "0";
            String club = cells.size() > 3 ? cells.get(3).text().trim() : "";
            
            // If club is empty and we have only 3 cells, the third cell might be the club
            if (club.isEmpty() && cells.size() == 3) {
                club = cells.get(2).text().trim();
                handicapIndexStr = "0";
                log.info("Using alternative format: 3 cells detected, treating cell 2 as club");
            }

            BigDecimal handicapIndex;
            try {
                if (handicapIndexStr.isEmpty() || handicapIndexStr.equals("0")) {
                    log.info("Empty or zero handicap index for matricula: {}, using 0", matricula);
                    handicapIndex = BigDecimal.ZERO;
                } else {
                    handicapIndex = new BigDecimal(handicapIndexStr.replace(",", "."));
                }
            } catch (NumberFormatException e) {
                log.error("Error parsing handicap index '{}' for matricula: {}. Using 0 as default.", 
                        handicapIndexStr, matricula, e);
                handicapIndex = BigDecimal.ZERO;
            }

            HandicapInfoDTO dto = HandicapInfoDTO.builder()
                    .nombreCompleto(nombreCompleto)
                    .matricula(matricula)
                    .handicapIndex(handicapIndex)
                    .club(club)
                    .build();

            log.info("Successfully fetched handicap info for matricula: {}", matricula);
            return dto;

        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching handicap info for matricula: {}", matricula, e);
            throw new BadRequestException("Error connecting to handicap service: " + e.getMessage());
        }
    }
}
