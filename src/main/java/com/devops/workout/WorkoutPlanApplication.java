package com.devops.workout;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Workout Plan Management System.
 * Launches the embedded Tomcat server on port 8080 (default).
 */
@SpringBootApplication
public class WorkoutPlanApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkoutPlanApplication.class, args);
    }
}
