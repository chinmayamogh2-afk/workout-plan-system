package com.devops.workout.controller;

import com.devops.workout.dto.CreateUserDto;
import com.devops.workout.model.AppUser;
import com.devops.workout.model.WorkoutPlan;
import com.devops.workout.model.WorkoutPlan.WorkoutStatus;
import com.devops.workout.repository.AppUserRepository;
import com.devops.workout.repository.WorkoutPlanRepository;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Set;

/**
 * Exclusive administration controller for system administrators (ROLE_ADMIN).
 * Features:
 *   - User management (view all accounts, create new trainers/members, toggle active status)
 *   - Pending approvals review queue (plans waiting in SUBMITTED state)
 *   - System metrics & health overview
 */
@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AppUserRepository userRepository;
    private final WorkoutPlanRepository planRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminController(AppUserRepository userRepository,
                           WorkoutPlanRepository planRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.planRepository = planRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/users")
    public String userManagement(Model model) {
        List<AppUser> users = userRepository.findAll();
        List<WorkoutPlan> pendingPlans = planRepository.findByStatus(WorkoutStatus.SUBMITTED);
        long totalPlans = planRepository.count();
        long publishedPlans = planRepository.countByStatus(WorkoutStatus.PUBLISHED);

        model.addAttribute("users", users);
        model.addAttribute("pendingPlans", pendingPlans);
        model.addAttribute("totalUsers", users.size());
        model.addAttribute("totalPlans", totalPlans);
        model.addAttribute("publishedPlans", publishedPlans);
        model.addAttribute("createUserDto", new CreateUserDto());
        return "admin/users";
    }

    @PostMapping("/users/new")
    public String createUser(@Valid @ModelAttribute("createUserDto") CreateUserDto dto,
                             BindingResult bindingResult,
                             Model model,
                             RedirectAttributes redirectAttrs) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("users", userRepository.findAll());
            model.addAttribute("pendingPlans", planRepository.findByStatus(WorkoutStatus.SUBMITTED));
            model.addAttribute("totalUsers", userRepository.count());
            model.addAttribute("totalPlans", planRepository.count());
            model.addAttribute("publishedPlans", planRepository.countByStatus(WorkoutStatus.PUBLISHED));
            return "admin/users";
        }

        if (userRepository.existsByUsername(dto.getUsername().trim())) {
            bindingResult.rejectValue("username", "error.user", "Username is already taken.");
            model.addAttribute("users", userRepository.findAll());
            model.addAttribute("pendingPlans", planRepository.findByStatus(WorkoutStatus.SUBMITTED));
            model.addAttribute("totalUsers", userRepository.count());
            model.addAttribute("totalPlans", planRepository.count());
            model.addAttribute("publishedPlans", planRepository.countByStatus(WorkoutStatus.PUBLISHED));
            return "admin/users";
        }

        if (userRepository.existsByEmail(dto.getEmail().trim())) {
            bindingResult.rejectValue("email", "error.user", "Email address is already registered.");
            model.addAttribute("users", userRepository.findAll());
            model.addAttribute("pendingPlans", planRepository.findByStatus(WorkoutStatus.SUBMITTED));
            model.addAttribute("totalUsers", userRepository.count());
            model.addAttribute("totalPlans", planRepository.count());
            model.addAttribute("publishedPlans", planRepository.countByStatus(WorkoutStatus.PUBLISHED));
            return "admin/users";
        }

        String assignedRole = "ROLE_TRAINER".equalsIgnoreCase(dto.getRole()) ? "ROLE_TRAINER" : "ROLE_USER";

        AppUser newUser = AppUser.builder()
                .username(dto.getUsername().trim())
                .password(passwordEncoder.encode(dto.getPassword()))
                .fullName(dto.getFullName().trim())
                .email(dto.getEmail().trim())
                .roles(Set.of(assignedRole))
                .enabled(true)
                .build();

        userRepository.save(newUser);
        redirectAttrs.addFlashAttribute("successMessage",
                "User '" + newUser.getUsername() + "' (" + (assignedRole.contains("TRAINER") ? "Trainer" : "Member") + ") created successfully!");
        return "redirect:/admin/users";
    }

    @PostMapping("/users/{id}/toggle-status")
    public String toggleUserStatus(@PathVariable Long id,
                                   Authentication auth,
                                   RedirectAttributes redirectAttrs) {
        AppUser user = userRepository.findById(id).orElse(null);
        if (user == null) {
            redirectAttrs.addFlashAttribute("errorMessage", "User not found.");
            return "redirect:/admin/users";
        }

        if (user.getUsername().equals(auth.getName())) {
            redirectAttrs.addFlashAttribute("errorMessage", "You cannot disable your own admin account!");
            return "redirect:/admin/users";
        }

        user.setEnabled(!user.isEnabled());
        userRepository.save(user);

        String statusStr = user.isEnabled() ? "enabled" : "disabled";
        redirectAttrs.addFlashAttribute("successMessage",
                "User '" + user.getUsername() + "' has been " + statusStr + ".");
        return "redirect:/admin/users";
    }
}
