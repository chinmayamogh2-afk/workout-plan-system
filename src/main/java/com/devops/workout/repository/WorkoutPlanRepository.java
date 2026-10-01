package com.devops.workout.repository;

import com.devops.workout.model.WorkoutPlan;
import com.devops.workout.model.WorkoutPlan.DifficultyLevel;
import com.devops.workout.model.WorkoutPlan.WorkoutStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for WorkoutPlan entities.
 */
@Repository
public interface WorkoutPlanRepository extends JpaRepository<WorkoutPlan, Long> {

    /** Find all plans by their workflow status. */
    List<WorkoutPlan> findByStatus(WorkoutStatus status);

    /** Find all plans by difficulty level. */
    List<WorkoutPlan> findByDifficulty(DifficultyLevel difficulty);

    /** Find plans created by a specific trainer. */
    List<WorkoutPlan> findByCreatedBy(String createdBy);

    /** Case-insensitive search across name, description, and target muscle group. */
    @Query("SELECT w FROM WorkoutPlan w WHERE " +
           "LOWER(w.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(w.description) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(w.targetMuscleGroup) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<WorkoutPlan> searchByKeyword(@Param("keyword") String keyword);

    /** Count plans grouped by status for dashboard. */
    long countByStatus(WorkoutStatus status);

    /** Count plans grouped by difficulty for dashboard. */
    long countByDifficulty(DifficultyLevel difficulty);

    /** Filtered search with optional status and difficulty. */
    @Query("SELECT w FROM WorkoutPlan w WHERE " +
           "(:status IS NULL OR w.status = :status) AND " +
           "(:difficulty IS NULL OR w.difficulty = :difficulty) AND " +
           "(:keyword IS NULL OR LOWER(w.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(w.targetMuscleGroup) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<WorkoutPlan> filterPlans(@Param("status") WorkoutStatus status,
                                  @Param("difficulty") DifficultyLevel difficulty,
                                  @Param("keyword") String keyword);
}
