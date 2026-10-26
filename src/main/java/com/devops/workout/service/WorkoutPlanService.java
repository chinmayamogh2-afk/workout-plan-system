package com.devops.workout.service;

import com.devops.workout.dto.DashboardStatsDto;
import com.devops.workout.dto.WorkoutPlanDto;
import com.devops.workout.exception.InvalidStatusTransitionException;
import com.devops.workout.exception.ResourceNotFoundException;
import com.devops.workout.model.WorkoutPlan;
import com.devops.workout.model.WorkoutPlan.DifficultyLevel;
import com.devops.workout.model.WorkoutPlan.WorkoutStatus;
import com.devops.workout.repository.WorkoutPlanRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business-logic layer for WorkoutPlan operations.
 * All database interactions are done through WorkoutPlanRepository.
 */
@Service
@Transactional
public class WorkoutPlanService {

    private final WorkoutPlanRepository repository;

    public WorkoutPlanService(WorkoutPlanRepository repository) {
        this.repository = repository;
    }

    // ------------------------------------------------------------------ //
    //  CRUD                                                                //
    // ------------------------------------------------------------------ //

    /** Create a new workout plan owned by the given user. */
    public WorkoutPlan createPlan(WorkoutPlanDto dto, String createdBy) {
        WorkoutPlan plan = mapDtoToEntity(dto);
        plan.setCreatedBy(createdBy);
        plan.setStatus(WorkoutStatus.DRAFT);
        return repository.save(plan);
    }

    /** Return ALL plans. Unscoped: callers must restrict this to administrators. */
    @Transactional(readOnly = true)
    public List<WorkoutPlan> getAllPlans() {
        return repository.findAll();
    }

    /** Return plans created by a specific user (trainer view). */
    @Transactional(readOnly = true)
    public List<WorkoutPlan> getPlansByUser(String username) {
        return repository.findByCreatedBy(username);
    }

    /** Find a single plan by ID or throw ResourceNotFoundException. */
    @Transactional(readOnly = true)
    public WorkoutPlan getPlanById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Workout plan not found with id: " + id));
    }

    /** Find a plan the caller is allowed to see (owner or admin), else AccessDeniedException. */
    @Transactional(readOnly = true)
    public WorkoutPlan getPlanForUser(Long id, String requestingUser, boolean isAdmin) {
        WorkoutPlan plan = getPlanById(id);
        assertOwnerOrAdmin(plan, requestingUser, isAdmin, "You do not have access to this workout plan.");
        return plan;
    }

    /** Find a plan the caller may edit (owner or admin; trainers cannot edit PUBLISHED plans). */
    @Transactional(readOnly = true)
    public WorkoutPlan getEditablePlan(Long id, String requestingUser, boolean isAdmin) {
        WorkoutPlan plan = getPlanById(id);
        assertOwnerOrAdmin(plan, requestingUser, isAdmin, "You can only edit your own workout plans.");
        if (plan.getStatus() == WorkoutStatus.PUBLISHED && !isAdmin) {
            throw new InvalidStatusTransitionException("Published plans cannot be edited by trainers.");
        }
        return plan;
    }

    /** Update an existing plan. Only the owner or an admin may edit. */
    public WorkoutPlan updatePlan(Long id, WorkoutPlanDto dto, String requestingUser, boolean isAdmin) {
        WorkoutPlan existing = getPlanById(id);

        assertOwnerOrAdmin(existing, requestingUser, isAdmin, "You can only edit your own workout plans.");
        if (existing.getStatus() == WorkoutStatus.PUBLISHED && !isAdmin) {
            throw new InvalidStatusTransitionException("Published plans cannot be edited by trainers.");
        }

        existing.setName(dto.getName());
        existing.setDescription(dto.getDescription());
        existing.setExerciseDetails(dto.getExerciseDetails());
        existing.setDurationMinutes(dto.getDurationMinutes());
        existing.setDifficulty(dto.getDifficulty());
        existing.setTargetMuscleGroup(dto.getTargetMuscleGroup());
        existing.setTrainerName(dto.getTrainerName());

        return repository.save(existing);
    }

    /** Delete a plan. Only the owner (while in DRAFT) or admin. */
    public void deletePlan(Long id, String requestingUser, boolean isAdmin) {
        WorkoutPlan plan = getPlanById(id);
        assertOwnerOrAdmin(plan, requestingUser, isAdmin, "You can only delete your own workout plans.");
        if (!isAdmin && plan.getStatus() != WorkoutStatus.DRAFT) {
            throw new InvalidStatusTransitionException("Trainers can only delete plans that are still in DRAFT.");
        }
        repository.deleteById(id);
    }

    // ------------------------------------------------------------------ //
    //  Workflow / Status transitions                                        //
    // ------------------------------------------------------------------ //

    /**
     * Transition a plan to a new status.
     *
     * Rules:
     *   DRAFT → SUBMITTED   : owner (TRAINER/ADMIN)
     *   SUBMITTED → APPROVED : ADMIN only
     *   APPROVED → PUBLISHED : ADMIN only
     *   SUBMITTED → DRAFT    : ADMIN (reject/revise)
     */
    public WorkoutPlan changeStatus(Long id, WorkoutStatus newStatus,
                                    String requestingUser, boolean isAdmin) {
        WorkoutPlan plan = getPlanById(id);
        // Authorization first: a non-owner must never learn anything about another user's plan.
        assertOwnerOrAdmin(plan, requestingUser, isAdmin, "You can only change the status of your own plans.");
        WorkoutStatus current = plan.getStatus();

        if (!current.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException(
                    String.format("Cannot transition from %s to %s", current, newStatus));
        }

        // TRAINER can only submit (DRAFT → SUBMITTED)
        if (!isAdmin && newStatus != WorkoutStatus.SUBMITTED) {
            throw new AccessDeniedException(
                    "Only admins can perform this status transition.");
        }

        plan.setStatus(newStatus);
        return repository.save(plan);
    }

    // ------------------------------------------------------------------ //
    //  Search / Filter                                                      //
    // ------------------------------------------------------------------ //

    /** Unscoped keyword search. Administrators only; trainers must use filterPlansForUser. */
    @Transactional(readOnly = true)
    public List<WorkoutPlan> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return repository.findAll();
        }
        return repository.searchByKeyword(keyword.trim());
    }

    /** Unscoped filter. Administrators only; trainers must use filterPlansForUser. */
    @Transactional(readOnly = true)
    public List<WorkoutPlan> filterPlans(WorkoutStatus status,
                                         DifficultyLevel difficulty,
                                         String keyword) {
        return repository.filterPlans(status, difficulty,
                (keyword == null || keyword.isBlank()) ? null : keyword.trim());
    }

    /** Search restricted to what the caller may see. */
    @Transactional(readOnly = true)
    public List<WorkoutPlan> filterPlansForUser(String requestingUser, boolean isAdmin,
                                                WorkoutStatus status,
                                                DifficultyLevel difficulty,
                                                String keyword) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        if (isAdmin) {
            return repository.filterPlans(status, difficulty, kw);
        }
        return repository.filterPlansByOwner(requestingUser, status, difficulty, kw);
    }

    // ------------------------------------------------------------------ //
    //  Dashboard                                                            //
    // ------------------------------------------------------------------ //

    @Transactional(readOnly = true)
    public DashboardStatsDto getDashboardStats() {
        return DashboardStatsDto.builder()
                .totalPlans(repository.count())
                .draftCount(repository.countByStatus(WorkoutStatus.DRAFT))
                .submittedCount(repository.countByStatus(WorkoutStatus.SUBMITTED))
                .approvedCount(repository.countByStatus(WorkoutStatus.APPROVED))
                .publishedCount(repository.countByStatus(WorkoutStatus.PUBLISHED))
                .beginnerCount(repository.countByDifficulty(DifficultyLevel.BEGINNER))
                .intermediateCount(repository.countByDifficulty(DifficultyLevel.INTERMEDIATE))
                .advancedCount(repository.countByDifficulty(DifficultyLevel.ADVANCED))
                .build();
    }

    /** Dashboard statistics limited to the caller's own plans unless admin. */
    @Transactional(readOnly = true)
    public DashboardStatsDto getDashboardStats(String requestingUser, boolean isAdmin) {
        if (isAdmin) {
            return getDashboardStats();
        }
        return DashboardStatsDto.builder()
                .totalPlans(repository.countByCreatedBy(requestingUser))
                .draftCount(repository.countByCreatedByAndStatus(requestingUser, WorkoutStatus.DRAFT))
                .submittedCount(repository.countByCreatedByAndStatus(requestingUser, WorkoutStatus.SUBMITTED))
                .approvedCount(repository.countByCreatedByAndStatus(requestingUser, WorkoutStatus.APPROVED))
                .publishedCount(repository.countByCreatedByAndStatus(requestingUser, WorkoutStatus.PUBLISHED))
                .beginnerCount(repository.countByCreatedByAndDifficulty(requestingUser, DifficultyLevel.BEGINNER))
                .intermediateCount(repository.countByCreatedByAndDifficulty(requestingUser, DifficultyLevel.INTERMEDIATE))
                .advancedCount(repository.countByCreatedByAndDifficulty(requestingUser, DifficultyLevel.ADVANCED))
                .build();
    }

    /** Five most recent plans the caller may see. */
    @Transactional(readOnly = true)
    public List<WorkoutPlan> getRecentPlans(String requestingUser, boolean isAdmin) {
        return isAdmin
                ? repository.findTop5ByOrderByCreatedAtDesc()
                : repository.findTop5ByCreatedByOrderByCreatedAtDesc(requestingUser);
    }

    // ------------------------------------------------------------------ //
    //  Authorization helper                                                 //
    // ------------------------------------------------------------------ //

    private void assertOwnerOrAdmin(WorkoutPlan plan, String requestingUser,
                                    boolean isAdmin, String message) {
        if (!isAdmin && (requestingUser == null || !requestingUser.equals(plan.getCreatedBy()))) {
            throw new AccessDeniedException(message);
        }
    }

    // ------------------------------------------------------------------ //
    //  Mapping helpers                                                      //
    // ------------------------------------------------------------------ //

    private WorkoutPlan mapDtoToEntity(WorkoutPlanDto dto) {
        return WorkoutPlan.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .exerciseDetails(dto.getExerciseDetails())
                .durationMinutes(dto.getDurationMinutes())
                .difficulty(dto.getDifficulty())
                .targetMuscleGroup(dto.getTargetMuscleGroup())
                .trainerName(dto.getTrainerName())
                .build();
    }

    public WorkoutPlanDto mapEntityToDto(WorkoutPlan plan) {
        return WorkoutPlanDto.builder()
                .id(plan.getId())
                .name(plan.getName())
                .description(plan.getDescription())
                .exerciseDetails(plan.getExerciseDetails())
                .durationMinutes(plan.getDurationMinutes())
                .difficulty(plan.getDifficulty())
                .targetMuscleGroup(plan.getTargetMuscleGroup())
                .trainerName(plan.getTrainerName())
                .status(plan.getStatus())
                .build();
    }
}
