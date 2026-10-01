package com.devops.workout.config;

import com.devops.workout.model.AppUser;
import com.devops.workout.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

/**
 * Seeds default users on first startup if they do not already exist.
 *
 * Default credentials (for local development only):
 *   admin   / Admin@123   (ROLE_ADMIN, ROLE_TRAINER)
 *   trainer / Trainer@123 (ROLE_TRAINER)
 *
 * In production, supply users via the database init script or environment variables.
 */
@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    CommandLineRunner seedUsers(AppUserRepository repo, PasswordEncoder encoder) {
        return args -> {
            if (!repo.existsByUsername("admin")) {
                repo.save(AppUser.builder()
                        .username("admin")
                        .password(encoder.encode("Admin@123"))
                        .fullName("System Admin")
                        .email("admin@workout.local")
                        .roles(Set.of("ROLE_ADMIN", "ROLE_TRAINER"))
                        .build());
                log.info("Created default admin user");
            }

            if (!repo.existsByUsername("trainer")) {
                repo.save(AppUser.builder()
                        .username("trainer")
                        .password(encoder.encode("Trainer@123"))
                        .fullName("Default Trainer")
                        .email("trainer@workout.local")
                        .roles(Set.of("ROLE_TRAINER"))
                        .build());
                log.info("Created default trainer user");
            }
        };
    }
}
