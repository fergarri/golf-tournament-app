package com.golf.tournament.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserRequest {
    
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;
    
    private String matricula;
    
    @NotBlank(message = "Role is required")
    private String role;

    /** Club al que pertenece el usuario. Requerido cuando role = USER; ignorado para role = ADMIN. */
    private Long courseId;
}
