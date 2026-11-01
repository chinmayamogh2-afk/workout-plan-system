package com.devops.workout.config;

import com.devops.workout.model.AppUser;
import com.devops.workout.model.WorkoutPlan;
import com.devops.workout.model.WorkoutPlan.DifficultyLevel;
import com.devops.workout.model.WorkoutPlan.WorkoutStatus;
import com.devops.workout.repository.AppUserRepository;
import com.devops.workout.repository.WorkoutPlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Set;

/**
 * Seeds default users and sample published plans on startup if they do not already exist.
 *
 * Default credentials (for local development only):
 *   admin   / Admin@123   (ROLE_ADMIN, ROLE_TRAINER)
 *   trainer / Trainer@123 (ROLE_TRAINER)
 *   user    / User@123    (ROLE_USER - Read-only viewer of published plans)
 */
@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private static final String DEV_ADMIN_PASSWORD = "Admin@123";
    private static final String DEV_TRAINER_PASSWORD = "Trainer@123";
    private static final String DEV_USER_PASSWORD = "User@123";

    @Bean
    CommandLineRunner seedData(AppUserRepository userRepo,
                               WorkoutPlanRepository planRepo,
                               PasswordEncoder encoder,
                               @Value("${app.seed.admin-password:" + DEV_ADMIN_PASSWORD + "}") String adminPassword,
                               @Value("${app.seed.trainer-password:" + DEV_TRAINER_PASSWORD + "}") String trainerPassword,
                               @Value("${app.seed.user-password:" + DEV_USER_PASSWORD + "}") String userPassword) {
        return args -> {
            if (DEV_ADMIN_PASSWORD.equals(adminPassword) || DEV_TRAINER_PASSWORD.equals(trainerPassword) || DEV_USER_PASSWORD.equals(userPassword)) {
                log.warn("Seed users use the built-in DEVELOPMENT passwords. "
                        + "Set APP_ADMIN_PASSWORD and APP_TRAINER_PASSWORD for any shared or deployed environment.");
            }
            if (!userRepo.existsByUsername("admin")) {
                userRepo.save(AppUser.builder()
                        .username("admin")
                        .password(encoder.encode(adminPassword))
                        .fullName("System Admin")
                        .email("admin@workout.local")
                        .roles(Set.of("ROLE_ADMIN", "ROLE_TRAINER"))
                        .build());
                log.info("Created default admin user");
            }

            if (!userRepo.existsByUsername("trainer")) {
                userRepo.save(AppUser.builder()
                        .username("trainer")
                        .password(encoder.encode(trainerPassword))
                        .fullName("Default Trainer")
                        .email("trainer@workout.local")
                        .roles(Set.of("ROLE_TRAINER"))
                        .build());
                log.info("Created default trainer user");
            }

            if (!userRepo.existsByUsername("user")) {
                userRepo.save(AppUser.builder()
                        .username("user")
                        .password(encoder.encode(userPassword))
                        .fullName("Regular Member")
                        .email("user@workout.local")
                        .roles(Set.of("ROLE_USER"))
                        .build());
                log.info("Created default member user (read-only)");
            }

            // Seed 3 sample published workout plans if database has none
            if (planRepo.count() == 0) {
                planRepo.saveAll(List.of(
                        WorkoutPlan.builder()
                                .name("Full Body Beginner Strength")
                                .description("A balanced full-body foundation routine focusing on compound movements, core stability, and functional mobility.")
                                .exerciseDetails("1. Bodyweight Squats – 3 sets x 12 reps\n2. Push-ups – 3 sets x 10 reps\n3. Dumbbell Romanian Deadlifts – 3 sets x 12 reps\n4. Plank Hold – 3 sets x 45 seconds\n5. Dumbbell Overhead Press – 3 sets x 10 reps")
                                .durationMinutes(45)
                                .difficulty(DifficultyLevel.BEGINNER)
                                .targetMuscleGroup("Full Body")
                                .trainerName("Default Trainer")
                                .createdBy("trainer")
                                .status(WorkoutStatus.PUBLISHED)
                                .build(),

                        WorkoutPlan.builder()
                                .name("HIIT Core & Cardio Blast")
                                .description("High-intensity interval training designed to maximize calorie burn, cardiovascular endurance, and core strength.")
                                .exerciseDetails("1. Mountain Climbers – 40s work, 20s rest x 4 rounds\n2. Bicycle Crunches – 40s work, 20s rest x 4 rounds\n3. High Knees Sprint – 40s work, 20s rest x 4 rounds\n4. Russian Twists – 40s work, 20s rest x 4 rounds\n5. Burpees – 40s work, 20s rest x 4 rounds")
                                .durationMinutes(30)
                                .difficulty(DifficultyLevel.INTERMEDIATE)
                                .targetMuscleGroup("Core")
                                .trainerName("Default Trainer")
                                .createdBy("trainer")
                                .status(WorkoutStatus.PUBLISHED)
                                .build(),

                        WorkoutPlan.builder()
                                .name("Advanced Upper Body Hypertrophy")
                                .description("Heavy volume pressing and rowing sequence for maximum chest, back, and shoulder muscle development.")
                                .exerciseDetails("1. Barbell Bench Press – 4 sets x 6-8 reps\n2. Weighted Pull-ups – 4 sets x 6-8 reps\n3. Incline Dumbbell Press – 3 sets x 10-12 reps\n4. Barbell Bent-Over Rows – 4 sets x 8-10 reps\n5. Dips to Failure – 3 sets\n6. Cable Face Pulls – 3 sets x 15 reps")
                                .durationMinutes(60)
                                .difficulty(DifficultyLevel.ADVANCED)
                                .targetMuscleGroup("Chest & Back")
                                .trainerName("System Admin")
                                .createdBy("admin")
                                .status(WorkoutStatus.PUBLISHED)
                                .build()
                ));
                log.info("Seeded 3 sample published workout plans");
            }
        };
    }
}
