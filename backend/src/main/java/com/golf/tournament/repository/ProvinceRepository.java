package com.golf.tournament.repository;

import com.golf.tournament.model.Province;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProvinceRepository extends JpaRepository<Province, Long> {
    List<Province> findByCountryIdOrderByNombreAsc(Long countryId);
}
