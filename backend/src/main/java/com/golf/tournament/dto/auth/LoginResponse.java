package com.golf.tournament.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    
    private String token;
    @Builder.Default
    private String type = "Bearer";
    private String email;
    private String role;
    private List<String> permissions;
}
