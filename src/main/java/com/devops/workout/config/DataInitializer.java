package com.devops.workout.config;

import com.devops.workout.model.AppUser;
import com.devops.workout.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
 * Override with APP_ADMIN_PASSWORD / APP_TRAINER_PASSWORD (property app.seed.*).
 * Docker Compose and Ansible require these to be set explicitly.
 */
@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private static final String DEV_ADMIN_PASSWORD = "Admin@123";
    private static final String DEV_TRAINER_PASSWORD = "Trainer@123";

    @Bean
    CommandLineRunner seedUsers(AppUserRepository repo, PasswordEncoder encoder,
                                @Value("${app.seed.admin-password:" + DEV_ADMIN_PASSWORD + "}") String adminPassword,
                                @Value("${app.seed.trainer-password:" + DEV_TRAINER_PASSWORD + "}") String trainerPassword) {
        return args -> {
            if (DEV_ADMIN_PASSWORD.equals(adminPassword) || DEV_TRAINER_PASSWORD.equals(trainerPassword)) {
                log.warn("Seed users use the built-in DEVELOPMENT passwords. "
                        + "Set APP_ADMIN_PASSWORD and APP_TRAINER_PASSWORD for any shared or deployed environment.");
            }
            if (!repo.existsByUsername("admin")) {
                repo.save(AppUser.builder()
                        .username("admin")
                        .password(encoder.encode(adminPassword))
                        .fullName("System Admin")
                        .email("admin@workout.local")
                        .roles(Set.of("ROLE_ADMIN", "ROLE_TRAINER"))
                        .build());
                log.info("Created default admin user");
            }

            if (!repo.existsByUsername("trainer")) {
                repo.save(AppUser.builder()
                        .username("trainer")
                        .password(encoder.encode(trainerPassword))
                        .fullName("Default Trainer")
                        .email("trainer@workout.local")
                        .roles(Set.of("ROLE_TRAINER"))
                        .build());
                log.info("Created default trainer user");
            }
        };
    }
}
