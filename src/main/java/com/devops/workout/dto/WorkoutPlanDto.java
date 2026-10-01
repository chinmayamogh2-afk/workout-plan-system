package com.devops.workout.dto;

import com.devops.workout.model.WorkoutPlan.DifficultyLevel;
import com.devops.workout.model.WorkoutPlan.WorkoutStatus;
import jakarta.validation.constraints.*;
import lombok.*;

/**
 * Data Transfer Object used for creating and editing a WorkoutPlan.
 * Decouples the view layer from the entity layer.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkoutPlanDto {

    private Long id;

    @NotBlank(message = "Workout name is required")
    @Size(min = 3, max = 150)
    private String name;

    @NotBlank(message = "Description is required")
    @Size(max = 1000)
    private String description;

    @NotBlank(message = "Exercise details are required")
    private String exerciseDetails;

    @NotNull(message = "Duration is required")
    @Min(1)
    @Max(600)
    private Integer durationMinutes;

    @NotNull(message = "Difficulty is required")
    private DifficultyLevel difficulty;

    @NotBlank(message = "Target muscle group is required")
    private String targetMuscleGroup;

    @NotBlank(message = "Trainer name is required")
    private String trainerName;

    private WorkoutStatus status;
}
