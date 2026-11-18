package com.golf.tournament.dto.course;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Nombre de un campo, para armar selects (por ejemplo el club de un jugador).
 * No incluye tees, hoyos ni ratings.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseCatalogDTO {

    private Long id;
    private String nombre;
}
