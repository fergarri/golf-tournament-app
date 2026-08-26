package com.golf.tournament.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    
    private Long id;
    private String email;
    private String matricula;
    private String role;
    /** Null para superadmins (rol ADMIN). */
    private Long courseId;
    private String courseName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
