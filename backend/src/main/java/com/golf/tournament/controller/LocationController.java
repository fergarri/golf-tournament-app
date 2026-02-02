package com.golf.tournament.controller;

import com.golf.tournament.dto.location.CountryDTO;
import com.golf.tournament.dto.location.ProvinceDTO;
import com.golf.tournament.service.LocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/locations")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class LocationController {

    private final LocationService locationService;

    @GetMapping("/countries")
    public ResponseEntity<List<CountryDTO>> getAllCountries() {
        log.info("GET /locations/countries - Getting all countries");
        List<CountryDTO> countries = locationService.getAllCountries();
        return ResponseEntity.ok(countries);
    }

    @GetMapping("/countries/{countryId}/provinces")
    public ResponseEntity<List<ProvinceDTO>> getProvincesByCountry(@PathVariable Long countryId) {
        log.info("GET /locations/countries/{}/provinces - Getting provinces for country", countryId);
        List<ProvinceDTO> provinces = locationService.getProvincesByCountryId(countryId);
        return ResponseEntity.ok(provinces);
    }
}
