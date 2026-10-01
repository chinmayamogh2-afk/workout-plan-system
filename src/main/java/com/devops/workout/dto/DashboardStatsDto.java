package com.devops.workout.dto;

import lombok.*;

/**
 * DTO carrying aggregate statistics shown on the dashboard.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardStatsDto {

    private long totalPlans;
    private long draftCount;
    private long submittedCount;
    private long approvedCount;
    private long publishedCount;
    private long beginnerCount;
    private long intermediateCount;
    private long advancedCount;
}
