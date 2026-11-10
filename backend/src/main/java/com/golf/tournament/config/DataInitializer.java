package com.golf.tournament.config;

import com.golf.tournament.model.Role;
import com.golf.tournament.model.User;
import com.golf.tournament.repository.UserRepository;
import com.golf.tournament.security.PasswordPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Crea el primer superadmin sólo en una base sin ningún usuario ADMIN y sólo si se
 * definen INITIAL_ADMIN_EMAIL e INITIAL_ADMIN_PASSWORD. No hay credenciales por defecto.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;

    @Value("${INITIAL_ADMIN_EMAIL:}")
    private String initialAdminEmail;

    @Value("${INITIAL_ADMIN_PASSWORD:}")
    private String initialAdminPassword;

    @Override
    public void run(String... args) {
        if (userRepository.existsByRole(Role.ADMIN)) {
            return;
        }

        if (initialAdminEmail.isBlank() || initialAdminPassword.isBlank()) {
            log.warn("No existe ningún superadmin. Definí INITIAL_ADMIN_EMAIL e INITIAL_ADMIN_PASSWORD para crearlo al iniciar.");
            return;
        }

        String email = initialAdminEmail.trim();
        if (userRepository.existsByEmail(email)) {
            log.warn("No se creó el superadmin inicial: ya existe un usuario con el email indicado.");
            return;
        }

        passwordPolicy.validate(initialAdminPassword);

        User admin = User.builder()
                .email(email)
                .password(passwordEncoder.encode(initialAdminPassword))
                .role(Role.ADMIN)
                .build();
        userRepository.save(admin);
        log.info("Superadmin inicial creado: {}", email);
    }
}
