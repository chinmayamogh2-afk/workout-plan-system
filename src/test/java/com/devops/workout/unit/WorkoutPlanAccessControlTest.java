package com.devops.workout.unit;

import com.devops.workout.dto.WorkoutPlanDto;
import com.devops.workout.exception.InvalidStatusTransitionException;
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
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Service-layer authorization rules: a trainer may only touch their own plans,
 * an admin may touch all. These guard against cross-user data access even if a
 * controller or template forgets to check.
 */
@ExtendWith(MockitoExtension.class)
class WorkoutPlanAccessControlTest {

    @Mock
    private WorkoutPlanRepository repository;

    @InjectMocks
    private WorkoutPlanService service;

    private WorkoutPlan alicePlan;

    @BeforeEach
    void setUp() {
        alicePlan = WorkoutPlan.builder()
                .id(10L).name("Alice Plan").description("d").exerciseDetails("e")
                .durationMinutes(30).difficulty(DifficultyLevel.BEGINNER)
                .targetMuscleGroup("Legs").trainerName("Alice")
                .createdBy("alice").status(WorkoutStatus.DRAFT).build();
        lenient().when(repository.findById(10L)).thenReturn(Optional.of(alicePlan));
    }

    private WorkoutPlanDto dto() {
        return WorkoutPlanDto.builder().name("Changed").description("d").exerciseDetails("e")
                .durationMinutes(20).difficulty(DifficultyLevel.ADVANCED)
                .targetMuscleGroup("Back").trainerName("Bob").build();
    }

    @Test
    @DisplayName("owner can view own plan; admin can view any plan")
    void view_ownerAndAdminAllowed() {
        assertThat(service.getPlanForUser(10L, "alice", false)).isSameAs(alicePlan);
        assertThat(service.getPlanForUser(10L, "admin", true)).isSameAs(alicePlan);
    }

    @Test
    @DisplayName("another trainer cannot view or open the edit form of a plan")
    void view_otherTrainerDenied() {
        assertThatThrownBy(() -> service.getPlanForUser(10L, "bob", false))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.getEditablePlan(10L, "bob", false))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("trainer cannot open the edit form of own PUBLISHED plan, admin can")
    void editForm_publishedBlockedForTrainer() {
        alicePlan.setStatus(WorkoutStatus.PUBLISHED);
        assertThatThrownBy(() -> service.getEditablePlan(10L, "alice", false))
                .isInstanceOf(InvalidStatusTransitionException.class);
        assertThat(service.getEditablePlan(10L, "admin", true)).isSameAs(alicePlan);
    }

    @Test
    @DisplayName("another trainer cannot update a plan and nothing is saved")
    void update_otherTrainerDenied() {
        assertThatThrownBy(() -> service.updatePlan(10L, dto(), "bob", false))
                .isInstanceOf(AccessDeniedException.class);
        verify(repository, never()).save(any());
        assertThat(alicePlan.getName()).isEqualTo("Alice Plan");
    }

    @Test
    @DisplayName("another trainer cannot delete a plan")
    void delete_otherTrainerDenied() {
        assertThatThrownBy(() -> service.deletePlan(10L, "bob", false))
                .isInstanceOf(AccessDeniedException.class);
        verify(repository, never()).deleteById(any());
    }

    @Test
    @DisplayName("owner may delete only DRAFT plans; admin may delete any")
    void delete_onlyDraftForTrainer() {
        service.deletePlan(10L, "alice", false);
        verify(repository).deleteById(10L);

        alicePlan.setStatus(WorkoutStatus.SUBMITTED);
        assertThatThrownBy(() -> service.deletePlan(10L, "alice", false))
                .isInstanceOf(InvalidStatusTransitionException.class);

        service.deletePlan(10L, "admin", true);
        verify(repository, org.mockito.Mockito.times(2)).deleteById(10L);
    }

    @Test
    @DisplayName("another trainer cannot change status, even to an otherwise valid state")
    void status_otherTrainerDenied() {
        assertThatThrownBy(() -> service.changeStatus(10L, WorkoutStatus.SUBMITTED, "bob", false))
                .isInstanceOf(AccessDeniedException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("ownership is checked before transition rules (no state leak to non-owners)")
    void status_nonOwnerGetsAccessDenied_notTransitionError() {
        // DRAFT -> PUBLISHED is an invalid transition, but a non-owner must still get 403.
        assertThatThrownBy(() -> service.changeStatus(10L, WorkoutStatus.PUBLISHED, "bob", false))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("trainer cannot approve or publish own plan")
    void status_trainerCannotApprove() {
        alicePlan.setStatus(WorkoutStatus.SUBMITTED);
        assertThatThrownBy(() -> service.changeStatus(10L, WorkoutStatus.APPROVED, "alice", false))
                .isInstanceOf(AccessDeniedException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("full admin workflow: SUBMITTED -> APPROVED -> PUBLISHED; PUBLISHED is terminal")
    void status_adminWorkflowAndTerminalState() {
        when(repository.save(any(WorkoutPlan.class))).thenAnswer(i -> i.getArgument(0));
        alicePlan.setStatus(WorkoutStatus.SUBMITTED);

        assertThat(service.changeStatus(10L, WorkoutStatus.APPROVED, "admin", true).getStatus())
                .isEqualTo(WorkoutStatus.APPROVED);
        assertThat(service.changeStatus(10L, WorkoutStatus.PUBLISHED, "admin", true).getStatus())
                .isEqualTo(WorkoutStatus.PUBLISHED);
        assertThatThrownBy(() -> service.changeStatus(10L, WorkoutStatus.DRAFT, "admin", true))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    @DisplayName("trainer search is restricted to the trainer's own plans")
    void filter_trainerUsesOwnerScopedQuery() {
        when(repository.filterPlansByOwner("alice", null, null, "legs")).thenReturn(List.of(alicePlan));

        assertThat(service.filterPlansForUser("alice", false, null, null, " legs ")).containsExactly(alicePlan);
        verify(repository, never()).filterPlans(any(), any(), any());
    }

    @Test
    @DisplayName("admin search is unrestricted")
    void filter_adminUsesGlobalQuery() {
        when(repository.filterPlans(WorkoutStatus.DRAFT, null, null)).thenReturn(List.of(alicePlan));

        assertThat(service.filterPlansForUser("admin", true, WorkoutStatus.DRAFT, null, "  "))
                .containsExactly(alicePlan);
        verify(repository, never()).filterPlansByOwner(any(), any(), any(), any());
    }

    @Test
    @DisplayName("trainer dashboard counts only the trainer's plans")
    void dashboard_trainerIsIsolated() {
        when(repository.countByCreatedBy("alice")).thenReturn(2L);
        when(repository.countByCreatedByAndStatus("alice", WorkoutStatus.DRAFT)).thenReturn(2L);

        var stats = service.getDashboardStats("alice", false);

        assertThat(stats.getTotalPlans()).isEqualTo(2L);
        assertThat(stats.getDraftCount()).isEqualTo(2L);
        verify(repository, never()).count();
        verify(repository, never()).countByStatus(any());
    }

    @Test
    @DisplayName("admin dashboard counts every plan")
    void dashboard_adminSeesGlobalCounts() {
        when(repository.count()).thenReturn(7L);

        assertThat(service.getDashboardStats("admin", true).getTotalPlans()).isEqualTo(7L);
        verify(repository, never()).countByCreatedBy(any());
    }

    @Test
    @DisplayName("recent plans are owner-scoped for trainers")
    void recentPlans_trainerScoped() {
        when(repository.findTop5ByCreatedByOrderByCreatedAtDesc("alice")).thenReturn(List.of(alicePlan));

        assertThat(service.getRecentPlans("alice", false)).containsExactly(alicePlan);
        verify(repository, never()).findTop5ByOrderByCreatedAtDesc();
    }
}
