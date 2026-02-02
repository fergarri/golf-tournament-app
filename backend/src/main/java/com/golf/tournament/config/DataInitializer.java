package com.golf.tournament.config;

import com.golf.tournament.model.User;
import com.golf.tournament.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (!userRepository.existsByEmail("admin@golftournament.com")) {
            User admin = User.builder()
                    .email("admin@golftournament.com")
                    .matricula("ADMIN001")
                    .password(passwordEncoder.encode("admin123"))
                    .role("ADMIN")
                    .build();
            
            userRepository.save(admin);
            log.info("Default admin user created: admin@golftournament.com / admin123");
        } else {
            log.info("Admin user already exists");
        }
    }
}
