package com.golf.tournament.service;

import com.golf.tournament.config.CustomUserDetails;
import com.golf.tournament.config.JwtUtil;
import com.golf.tournament.dto.auth.ChangeOwnPasswordRequest;
import com.golf.tournament.dto.auth.LoginRequest;
import com.golf.tournament.dto.auth.LoginResponse;
import com.golf.tournament.dto.auth.ProfileResponse;
import com.golf.tournament.dto.auth.UpdateOwnProfileRequest;
import com.golf.tournament.exception.BadRequestException;
import com.golf.tournament.exception.UnauthorizedException;
import com.golf.tournament.model.Permission;
import com.golf.tournament.model.User;
import com.golf.tournament.repository.UserRepository;
import com.golf.tournament.security.CurrentUserProvider;
import com.golf.tournament.security.PasswordPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String EMAIL_PATTERN = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$";

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final CurrentUserProvider currentUserProvider;

    public LoginResponse login(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );

            UserDetails userDetails = userDetailsService.loadUserByUsername(request.getUsername());
            CustomUserDetails customUserDetails = (CustomUserDetails) userDetails;
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
                    .courseId(customUserDetails.getCourseId())
                    .courseName(customUserDetails.getCourseName())
                    .build();

        } catch (Exception e) {
            log.error("Login failed for user: {}", request.getUsername(), e);
            throw new UnauthorizedException("Invalid credentials");
        }
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile() {
        return toProfile(currentUser(), null);
    }

    @Transactional
    public ProfileResponse updateProfile(UpdateOwnProfileRequest request) {
        if (request == null) {
            throw new BadRequestException("El email es obligatorio");
        }
        User user = currentUser();
        String newEmail = request.getEmail() == null ? "" : request.getEmail().trim();
        if (newEmail.isEmpty()) {
            throw new BadRequestException("El email es obligatorio");
        }
        if (!newEmail.matches(EMAIL_PATTERN)) {
            throw new BadRequestException("El email no es válido");
        }

        boolean emailChanged = !newEmail.equals(user.getEmail());
        if (emailChanged && userRepository.existsByEmail(newEmail)) {
            throw new BadRequestException("Ya existe un usuario con ese email");
        }

        String token = null;
        if (emailChanged) {
            user.setEmail(newEmail);
            userRepository.saveAndFlush(user);
            UserDetails userDetails = userDetailsService.loadUserByUsername(newEmail);
            token = jwtUtil.generateToken(userDetails);
            log.info("Email updated for user id: {}", user.getId());
        }

        return toProfile(user, token);
    }

    @Transactional
    public void changeOwnPassword(ChangeOwnPasswordRequest request) {
        User user = currentUser();
        if (request == null || request.getCurrentPassword() == null || request.getCurrentPassword().isBlank()) {
            throw new BadRequestException("La contraseña actual es obligatoria");
        }

        passwordPolicy.validate(request.getNewPassword());
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BadRequestException("La contraseña actual es incorrecta");
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BadRequestException("La nueva contraseña no puede ser igual a la actual");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("Password changed by user id: {}", user.getId());
    }

    private User currentUser() {
        return userRepository.findById(currentUserProvider.getCurrentUserId())
                .orElseThrow(() -> new UnauthorizedException("No autenticado"));
    }

    private ProfileResponse toProfile(User user, String token) {
        return ProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .matricula(user.getMatricula())
                .role(user.getRole().name())
                .courseId(user.getCourse() != null ? user.getCourse().getId() : null)
                .courseName(user.getCourse() != null ? user.getCourse().getNombre() : null)
                .token(token)
                .build();
    }
}
