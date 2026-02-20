package com.golf.tournament.service;

import com.golf.tournament.config.JwtUtil;
import com.golf.tournament.dto.auth.LoginRequest;
import com.golf.tournament.dto.auth.LoginResponse;
import com.golf.tournament.exception.UnauthorizedException;
import com.golf.tournament.model.Permission;
import com.golf.tournament.model.User;
import com.golf.tournament.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public LoginResponse login(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );

            UserDetails userDetails = userDetailsService.loadUserByUsername(request.getUsername());
            String token = jwtUtil.generateToken(userDetails);

            User user = userRepository.findByEmail(request.getUsername())
                    .or(() -> userRepository.findByMatricula(request.getUsername()))
                    .orElseThrow(() -> new UnauthorizedException("User not found"));

            log.info("User {} logged in successfully", user.getEmail());

            return LoginResponse.builder()
                    .token(token)
                    .type("Bearer")
                    .email(user.getEmail())
                    .role(user.getRole().name())
                    .permissions(user.getRole().getPermissions().stream()
                            .map(Permission::name)
                            .toList())
                    .build();

        } catch (Exception e) {
            log.error("Login failed for user: {}", request.getUsername(), e);
            throw new UnauthorizedException("Invalid credentials");
        }
    }
}
