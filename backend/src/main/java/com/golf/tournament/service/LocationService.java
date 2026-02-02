package com.golf.tournament.service;

import com.golf.tournament.dto.location.CountryDTO;
import com.golf.tournament.dto.location.ProvinceDTO;
import com.golf.tournament.model.Country;
import com.golf.tournament.model.Province;
import com.golf.tournament.repository.CountryRepository;
import com.golf.tournament.repository.ProvinceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class LocationService {

    private final CountryRepository countryRepository;
    private final ProvinceRepository provinceRepository;

    public List<CountryDTO> getAllCountries() {
        log.debug("Fetching all countries");
        List<Country> countries = countryRepository.findAllByOrderByNombreAsc();
        return countries.stream()
                .map(this::toCountryDTO)
                .collect(Collectors.toList());
    }

    public List<ProvinceDTO> getProvincesByCountryId(Long countryId) {
        log.debug("Fetching provinces for country ID: {}", countryId);
        List<Province> provinces = provinceRepository.findByCountryIdOrderByNombreAsc(countryId);
        return provinces.stream()
                .map(this::toProvinceDTO)
                .collect(Collectors.toList());
    }

    private CountryDTO toCountryDTO(Country country) {
        return CountryDTO.builder()
                .id(country.getId())
                .nombre(country.getNombre())
                .codigoIso(country.getCodigoIso())
                .build();
    }

    private ProvinceDTO toProvinceDTO(Province province) {
        return ProvinceDTO.builder()
                .id(province.getId())
                .countryId(province.getCountry().getId())
                .nombre(province.getNombre())
                .build();
    }
}
