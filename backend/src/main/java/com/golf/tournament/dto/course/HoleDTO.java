package com.golf.tournament.dto.course;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoleDTO {
    
    private Long id;
    private Integer numeroHoyo;
    private Integer par;
    private Integer handicap;
    private Map<Long, Integer> distancesByTee;
}
