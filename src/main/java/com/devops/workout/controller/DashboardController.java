package com.devops.workout.controller;

import com.devops.workout.service.WorkoutPlanService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for the dashboard and home redirects.
 */
@Controller
public class DashboardController {

    private final WorkoutPlanService service;

    public DashboardController(WorkoutPlanService service) {
        this.service = service;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication auth, Model model) {
        model.addAttribute("stats", service.getDashboardStats());
        model.addAttribute("recentPlans", service.getAllPlans()
                .stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(5)
                .toList());
        model.addAttribute("username", auth.getName());
        return "dashboard";
    }
}
