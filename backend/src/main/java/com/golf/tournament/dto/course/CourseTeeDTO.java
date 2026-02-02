package com.golf.tournament.dto.course;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseTeeDTO {
    
    private Long id;
    private Long courseId;
    private String nombre;
    private String grupo;
    private Boolean active;
}
