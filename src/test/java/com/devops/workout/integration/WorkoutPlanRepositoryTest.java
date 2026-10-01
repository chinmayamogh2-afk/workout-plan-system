package com.devops.workout.integration;

import com.devops.workout.model.WorkoutPlan;
import com.devops.workout.model.WorkoutPlan.DifficultyLevel;
import com.devops.workout.model.WorkoutPlan.WorkoutStatus;
import com.devops.workout.repository.WorkoutPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for WorkoutPlanRepository using H2 in-memory DB.
 * Spring Boot's @DataJpaTest configures only the JPA slice.
 */
@DataJpaTest
@ActiveProfiles("test")
class WorkoutPlanRepositoryTest {

    @Autowired
    private WorkoutPlanRepository repository;

    @BeforeEach
    void setup() {
        repository.deleteAll();

        repository.save(WorkoutPlan.builder()
                .name("Beginner Cardio")
                .description("Light cardio for beginners")
                .exerciseDetails("Jog 20 min, Skip 10 min")
                .durationMinutes(30)
                .difficulty(DifficultyLevel.BEGINNER)
                .targetMuscleGroup("Cardiovascular")
                .trainerName("Alice")
                .createdBy("alice")
                .status(WorkoutStatus.DRAFT)
                .build());

        repository.save(WorkoutPlan.builder()
                .name("Advanced Strength")
                .description("Heavy compound lifts")
                .exerciseDetails("Deadlift 5x5, Squat 5x5")
                .durationMinutes(60)
                .difficulty(DifficultyLevel.ADVANCED)
                .targetMuscleGroup("Full Body")
                .trainerName("Bob")
                .createdBy("bob")
                .status(WorkoutStatus.PUBLISHED)
                .build());

        repository.save(WorkoutPlan.builder()
                .name("Core Builder")
                .description("Core strengthening routine")
                .exerciseDetails("Plank 3x1min, Crunches 3x20")
                .durationMinutes(25)
                .difficulty(DifficultyLevel.INTERMEDIATE)
                .targetMuscleGroup("Core")
                .trainerName("Alice")
                .createdBy("alice")
                .status(WorkoutStatus.SUBMITTED)
                .build());
    }

    @Test
    @DisplayName("findByStatus should return only plans with matching status")
    void findByStatus_returnsMatchingPlans() {
        List<WorkoutPlan> drafts = repository.findByStatus(WorkoutStatus.DRAFT);
        assertThat(drafts).hasSize(1);
        assertThat(drafts.get(0).getName()).isEqualTo("Beginner Cardio");
    }

    @Test
    @DisplayName("findByDifficulty should return only matching difficulty")
    void findByDifficulty_returnsMatchingPlans() {
        List<WorkoutPlan> advanced = repository.findByDifficulty(DifficultyLevel.ADVANCED);
        assertThat(advanced).hasSize(1);
        assertThat(advanced.get(0).getName()).isEqualTo("Advanced Strength");
    }

    @Test
    @DisplayName("findByCreatedBy should return plans by that username")
    void findByCreatedBy_returnsUserPlans() {
        List<WorkoutPlan> alicePlans = repository.findByCreatedBy("alice");
        assertThat(alicePlans).hasSize(2);
    }

    @Test
    @DisplayName("searchByKeyword should match name, description, or muscle group")
    void searchByKeyword_matchesAcrossFields() {
        List<WorkoutPlan> results = repository.searchByKeyword("core");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Core Builder");
    }

    @Test
    @DisplayName("searchByKeyword case-insensitive should work")
    void searchByKeyword_isCaseInsensitive() {
        List<WorkoutPlan> results = repository.searchByKeyword("CARDIO");
        assertThat(results).hasSize(1);
    }

    @Test
    @DisplayName("countByStatus should return correct count")
    void countByStatus_returnsCorrectCount() {
        assertThat(repository.countByStatus(WorkoutStatus.DRAFT)).isEqualTo(1L);
        assertThat(repository.countByStatus(WorkoutStatus.PUBLISHED)).isEqualTo(1L);
    }

    @Test
    @DisplayName("filterPlans with status filter should return filtered results")
    void filterPlans_withStatus_returnsFiltered() {
        List<WorkoutPlan> results = repository.filterPlans(WorkoutStatus.PUBLISHED, null, null);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getDifficulty()).isEqualTo(DifficultyLevel.ADVANCED);
    }

    @Test
    @DisplayName("filterPlans with difficulty and keyword should work combined")
    void filterPlans_withDifficultyAndKeyword_returnsFiltered() {
        List<WorkoutPlan> results = repository.filterPlans(null, DifficultyLevel.BEGINNER, "cardio");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getName()).isEqualTo("Beginner Cardio");
    }
}
