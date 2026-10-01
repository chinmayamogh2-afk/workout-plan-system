package com.devops.workout.unit;

import com.devops.workout.dto.WorkoutPlanDto;
import com.devops.workout.exception.InvalidStatusTransitionException;
import com.devops.workout.exception.ResourceNotFoundException;
import com.devops.workout.model.WorkoutPlan;
import com.devops.workout.model.WorkoutPlan.DifficultyLevel;
import com.devops.workout.model.WorkoutPlan.WorkoutStatus;
import com.devops.workout.repository.WorkoutPlanRepository;
import com.devops.workout.service.WorkoutPlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WorkoutPlanService.
 * Uses Mockito to mock the repository – no database required.
 */
@ExtendWith(MockitoExtension.class)
class WorkoutPlanServiceTest {

    @Mock
    private WorkoutPlanRepository repository;

    @InjectMocks
    private WorkoutPlanService service;

    private WorkoutPlan samplePlan;
    private WorkoutPlanDto sampleDto;

    @BeforeEach
    void setUp() {
        samplePlan = WorkoutPlan.builder()
                .id(1L)
                .name("Full Body Blast")
                .description("A comprehensive full-body workout")
                .exerciseDetails("Squat 3x10, Push-up 3x15, Pull-up 3x8")
                .durationMinutes(45)
                .difficulty(DifficultyLevel.INTERMEDIATE)
                .targetMuscleGroup("Full Body")
                .trainerName("John Doe")
                .createdBy("trainer")
                .status(WorkoutStatus.DRAFT)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        sampleDto = WorkoutPlanDto.builder()
                .name("Full Body Blast")
                .description("A comprehensive full-body workout")
                .exerciseDetails("Squat 3x10, Push-up 3x15, Pull-up 3x8")
                .durationMinutes(45)
                .difficulty(DifficultyLevel.INTERMEDIATE)
                .targetMuscleGroup("Full Body")
                .trainerName("John Doe")
                .build();
    }

    // ------------------------------------------------------------------ //
    //  CREATE                                                               //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("createPlan should save and return a new DRAFT plan")
    void createPlan_shouldReturnSavedPlan() {
        when(repository.save(any(WorkoutPlan.class))).thenReturn(samplePlan);

        WorkoutPlan result = service.createPlan(sampleDto, "trainer");

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Full Body Blast");
        verify(repository, times(1)).save(any(WorkoutPlan.class));
    }

    // ------------------------------------------------------------------ //
    //  READ                                                                 //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("getPlanById should return plan when ID exists")
    void getPlanById_shouldReturnPlan_whenFound() {
        when(repository.findById(1L)).thenReturn(Optional.of(samplePlan));

        WorkoutPlan result = service.getPlanById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Full Body Blast");
    }

    @Test
    @DisplayName("getPlanById should throw ResourceNotFoundException when not found")
    void getPlanById_shouldThrow_whenNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPlanById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("getAllPlans should return all plans from repository")
    void getAllPlans_shouldReturnAllPlans() {
        when(repository.findAll()).thenReturn(List.of(samplePlan));

        List<WorkoutPlan> result = service.getAllPlans();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Full Body Blast");
    }

    // ------------------------------------------------------------------ //
    //  UPDATE                                                               //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("updatePlan should save updated fields")
    void updatePlan_shouldUpdateAndSave() {
        when(repository.findById(1L)).thenReturn(Optional.of(samplePlan));
        when(repository.save(any(WorkoutPlan.class))).thenReturn(samplePlan);

        sampleDto.setName("Updated Plan");
        WorkoutPlan updated = service.updatePlan(1L, sampleDto, "trainer", false);

        verify(repository).save(any(WorkoutPlan.class));
    }

    // ------------------------------------------------------------------ //
    //  STATUS TRANSITIONS                                                   //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("changeStatus DRAFT→SUBMITTED should succeed for owner")
    void changeStatus_draftToSubmitted_shouldSucceed() {
        when(repository.findById(1L)).thenReturn(Optional.of(samplePlan));
        when(repository.save(any(WorkoutPlan.class))).thenReturn(samplePlan);

        WorkoutPlan result = service.changeStatus(1L, WorkoutStatus.SUBMITTED, "trainer", false);

        verify(repository).save(any(WorkoutPlan.class));
    }

    @Test
    @DisplayName("changeStatus DRAFT→PUBLISHED should throw InvalidStatusTransitionException")
    void changeStatus_invalidTransition_shouldThrow() {
        when(repository.findById(1L)).thenReturn(Optional.of(samplePlan));

        assertThatThrownBy(() -> service.changeStatus(1L, WorkoutStatus.PUBLISHED, "admin", true))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    @DisplayName("changeStatus SUBMITTED→APPROVED should succeed for admin")
    void changeStatus_submittedToApproved_shouldSucceedForAdmin() {
        samplePlan.setStatus(WorkoutStatus.SUBMITTED);
        when(repository.findById(1L)).thenReturn(Optional.of(samplePlan));
        when(repository.save(any(WorkoutPlan.class))).thenReturn(samplePlan);

        service.changeStatus(1L, WorkoutStatus.APPROVED, "admin", true);

        verify(repository).save(any(WorkoutPlan.class));
    }

    // ------------------------------------------------------------------ //
    //  SEARCH                                                               //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("search should delegate to repository.searchByKeyword")
    void search_shouldCallRepository() {
        when(repository.searchByKeyword("blast")).thenReturn(List.of(samplePlan));

        List<WorkoutPlan> results = service.search("blast");

        assertThat(results).hasSize(1);
        verify(repository).searchByKeyword("blast");
    }

    @Test
    @DisplayName("search with blank keyword should return all plans")
    void search_blankKeyword_shouldReturnAll() {
        when(repository.findAll()).thenReturn(List.of(samplePlan));

        List<WorkoutPlan> results = service.search("  ");

        assertThat(results).hasSize(1);
        verify(repository).findAll();
    }

    // ------------------------------------------------------------------ //
    //  DASHBOARD                                                            //
    // ------------------------------------------------------------------ //

    @Test
    @DisplayName("getDashboardStats should return correct counts")
    void getDashboardStats_shouldReturnStats() {
        when(repository.count()).thenReturn(5L);
        when(repository.countByStatus(WorkoutStatus.DRAFT)).thenReturn(2L);
        when(repository.countByStatus(WorkoutStatus.SUBMITTED)).thenReturn(1L);
        when(repository.countByStatus(WorkoutStatus.APPROVED)).thenReturn(1L);
        when(repository.countByStatus(WorkoutStatus.PUBLISHED)).thenReturn(1L);
        when(repository.countByDifficulty(DifficultyLevel.BEGINNER)).thenReturn(2L);
        when(repository.countByDifficulty(DifficultyLevel.INTERMEDIATE)).thenReturn(2L);
        when(repository.countByDifficulty(DifficultyLevel.ADVANCED)).thenReturn(1L);

        var stats = service.getDashboardStats();

        assertThat(stats.getTotalPlans()).isEqualTo(5L);
        assertThat(stats.getDraftCount()).isEqualTo(2L);
        assertThat(stats.getPublishedCount()).isEqualTo(1L);
    }
}
