package com.devops.workout.unit;

import com.devops.workout.model.WorkoutPlan.WorkoutStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the WorkoutStatus transition rules defined in WorkoutPlan.
 * These are pure logic tests – no Spring context needed.
 */
class WorkoutStatusTransitionTest {

    @Test
    @DisplayName("DRAFT can transition to SUBMITTED")
    void draft_canTransitionTo_submitted() {
        assertThat(WorkoutStatus.DRAFT.canTransitionTo(WorkoutStatus.SUBMITTED)).isTrue();
    }

    @Test
    @DisplayName("DRAFT cannot skip to APPROVED")
    void draft_cannotTransitionTo_approved() {
        assertThat(WorkoutStatus.DRAFT.canTransitionTo(WorkoutStatus.APPROVED)).isFalse();
    }

    @Test
    @DisplayName("DRAFT cannot skip to PUBLISHED")
    void draft_cannotTransitionTo_published() {
        assertThat(WorkoutStatus.DRAFT.canTransitionTo(WorkoutStatus.PUBLISHED)).isFalse();
    }

    @Test
    @DisplayName("SUBMITTED can transition to APPROVED")
    void submitted_canTransitionTo_approved() {
        assertThat(WorkoutStatus.SUBMITTED.canTransitionTo(WorkoutStatus.APPROVED)).isTrue();
    }

    @Test
    @DisplayName("SUBMITTED can be rejected back to DRAFT")
    void submitted_canTransitionTo_draft() {
        assertThat(WorkoutStatus.SUBMITTED.canTransitionTo(WorkoutStatus.DRAFT)).isTrue();
    }

    @Test
    @DisplayName("APPROVED can transition to PUBLISHED")
    void approved_canTransitionTo_published() {
        assertThat(WorkoutStatus.APPROVED.canTransitionTo(WorkoutStatus.PUBLISHED)).isTrue();
    }

    @Test
    @DisplayName("PUBLISHED is a terminal state – no further transitions")
    void published_isTerminal() {
        for (WorkoutStatus next : WorkoutStatus.values()) {
            assertThat(WorkoutStatus.PUBLISHED.canTransitionTo(next)).isFalse();
        }
    }
}
