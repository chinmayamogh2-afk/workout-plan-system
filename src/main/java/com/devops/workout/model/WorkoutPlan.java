package com.devops.workout.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * JPA entity representing a Workout Plan.
 * Status follows a role-based workflow: DRAFT → SUBMITTED → APPROVED → PUBLISHED
 */
@Entity
@Table(name = "workout_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkoutPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Workout name is required")
    @Size(min = 3, max = 150, message = "Name must be between 3 and 150 characters")
    @Column(nullable = false, length = 150)
    private String name;

    @NotBlank(message = "Description is required")
    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @NotBlank(message = "Exercise details are required")
    @Column(name = "exercise_details", nullable = false, columnDefinition = "TEXT")
    private String exerciseDetails;

    @NotNull(message = "Duration is required")
    @Min(value = 1, message = "Duration must be at least 1 minute")
    @Max(value = 600, message = "Duration must not exceed 600 minutes")
    @Column(nullable = false)
    private Integer durationMinutes;

    @NotNull(message = "Difficulty level is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DifficultyLevel difficulty;

    @NotBlank(message = "Target muscle group is required")
    @Column(name = "target_muscle_group", nullable = false, length = 100)
    private String targetMuscleGroup;

    @NotBlank(message = "Trainer/user name is required")
    @Column(name = "trainer_name", nullable = false, length = 100)
    private String trainerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private WorkoutStatus status = WorkoutStatus.DRAFT;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Username of the user who created this plan (from Spring Security). */
    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    /**
     * Difficulty levels for workout plans.
     */
    public enum DifficultyLevel {
        BEGINNER, INTERMEDIATE, ADVANCED
    }

    /**
     * Status workflow: DRAFT → SUBMITTED → APPROVED → PUBLISHED
     */
    public enum WorkoutStatus {
        DRAFT, SUBMITTED, APPROVED, PUBLISHED;

        /** Returns true if transition to the given next status is allowed. */
        public boolean canTransitionTo(WorkoutStatus next) {
            return switch (this) {
                case DRAFT      -> next == SUBMITTED;
                case SUBMITTED  -> next == APPROVED || next == DRAFT;
                case APPROVED   -> next == PUBLISHED || next == SUBMITTED;
                case PUBLISHED  -> false; // terminal state
            };
        }
    }
}
