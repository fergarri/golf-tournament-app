package com.golf.tournament.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileResponse {

    private Long id;
    private String email;
    private String matricula;
    private String role;
    private Long courseId;
    private String courseName;
    /** Presente solo cuando el email cambió y hay que reemplazar el token. */
    private String token;
}
