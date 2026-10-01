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

    /** Return all plans (admin view). */
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

    /** Update an existing plan. Only the owner or an admin may edit. */
    public WorkoutPlan updatePlan(Long id, WorkoutPlanDto dto, String requestingUser, boolean isAdmin) {
        WorkoutPlan existing = getPlanById(id);

        if (!isAdmin && !existing.getCreatedBy().equals(requestingUser)) {
            throw new AccessDeniedException("You can only edit your own workout plans.");
        }
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
        if (!isAdmin && !plan.getCreatedBy().equals(requestingUser)) {
            throw new AccessDeniedException("You can only delete your own workout plans.");
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

        // Only the owner can submit their own plan
        if (newStatus == WorkoutStatus.SUBMITTED &&
                !plan.getCreatedBy().equals(requestingUser) && !isAdmin) {
            throw new AccessDeniedException("You can only submit your own plans.");
        }

        plan.setStatus(newStatus);
        return repository.save(plan);
    }

    // ------------------------------------------------------------------ //
    //  Search / Filter                                                      //
    // ------------------------------------------------------------------ //

    @Transactional(readOnly = true)
    public List<WorkoutPlan> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return repository.findAll();
        }
        return repository.searchByKeyword(keyword.trim());
    }

    @Transactional(readOnly = true)
    public List<WorkoutPlan> filterPlans(WorkoutStatus status,
                                         DifficultyLevel difficulty,
                                         String keyword) {
        return repository.filterPlans(status, difficulty,
                (keyword == null || keyword.isBlank()) ? null : keyword.trim());
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
