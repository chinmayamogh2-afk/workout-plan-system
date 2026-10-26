package com.devops.workout.controller;

import com.devops.workout.service.WorkoutPlanService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
        boolean isAdmin = auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
        model.addAttribute("stats", service.getDashboardStats(auth.getName(), isAdmin));
        model.addAttribute("recentPlans", service.getRecentPlans(auth.getName(), isAdmin));
        model.addAttribute("username", auth.getName());
        return "dashboard";
    }
}
