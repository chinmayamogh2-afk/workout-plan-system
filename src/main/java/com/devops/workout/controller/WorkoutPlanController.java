package com.devops.workout.controller;

import com.devops.workout.dto.WorkoutPlanDto;
import com.devops.workout.model.WorkoutPlan;
import com.devops.workout.model.WorkoutPlan.DifficultyLevel;
import com.devops.workout.model.WorkoutPlan.WorkoutStatus;
import com.devops.workout.service.WorkoutPlanService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * MVC controller for all Workout Plan web pages.
 */
@Controller
@RequestMapping("/workouts")
public class WorkoutPlanController {

    private final WorkoutPlanService service;

    public WorkoutPlanController(WorkoutPlanService service) {
        this.service = service;
    }

    // ------------------------------------------------------------------ //
    //  List & Search                                                        //
    // ------------------------------------------------------------------ //

    @GetMapping
    public String listPlans(@RequestParam(required = false) String keyword,
                            @RequestParam(required = false) WorkoutStatus status,
                            @RequestParam(required = false) DifficultyLevel difficulty,
                            Authentication auth,
                            Model model) {

        List<WorkoutPlan> plans;
        boolean isAdmin = isAdmin(auth);
        boolean isTrainer = isTrainer(auth);
        boolean isMember = !isAdmin && !isTrainer;

        if (isAdmin) {
            if (keyword != null || status != null || difficulty != null) {
                plans = service.filterPlans(status, difficulty, keyword);
            } else {
                plans = service.getAllPlans();
            }
        } else if (isTrainer) {
            if (keyword != null || status != null || difficulty != null) {
                plans = service.filterPlansForUser(auth.getName(), false, status, difficulty, keyword);
            } else {
                plans = service.getPlansByUser(auth.getName());
            }
        } else {
            // Member/viewer: read-only access to PUBLISHED plans only
            if (keyword != null || difficulty != null) {
                plans = service.filterPublishedPlans(difficulty, keyword);
            } else {
                plans = service.getPublishedPlans();
            }
        }

        model.addAttribute("plans", plans);
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedDifficulty", difficulty);
        model.addAttribute("statusValues", WorkoutStatus.values());
        model.addAttribute("difficultyValues", DifficultyLevel.values());
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("isTrainer", isTrainer);
        model.addAttribute("isMember", isMember);
        return "workout/list";
    }

    // ------------------------------------------------------------------ //
    //  View Detail                                                          //
    // ------------------------------------------------------------------ //

    @GetMapping("/{id}")
    public String viewPlan(@PathVariable Long id, Authentication auth, Model model) {
        boolean isAdmin = isAdmin(auth);
        boolean isTrainer = isTrainer(auth);
        WorkoutPlan plan = service.getPlanForUser(id, auth.getName(), isAdmin);
        model.addAttribute("plan", plan);
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("isTrainer", isTrainer);
        model.addAttribute("isMember", !isAdmin && !isTrainer);
        model.addAttribute("isOwner", plan.getCreatedBy() != null && plan.getCreatedBy().equals(auth.getName()));
        model.addAttribute("statusValues", WorkoutStatus.values());
        return "workout/detail";
    }

    // ------------------------------------------------------------------ //
    //  Create                                                               //
    // ------------------------------------------------------------------ //

    @GetMapping("/new")
    public String newPlanForm(Authentication auth, Model model) {
        assertCanManagePlans(auth);
        model.addAttribute("planDto", WorkoutPlanDto.builder()
                .trainerName(auth.getName())
                .build());
        model.addAttribute("difficultyValues", DifficultyLevel.values());
        model.addAttribute("formMode", "create");
        return "workout/form";
    }

    @PostMapping("/new")
    public String createPlan(@Valid @ModelAttribute("planDto") WorkoutPlanDto dto,
                             BindingResult bindingResult,
                             Authentication auth,
                             Model model,
                             RedirectAttributes redirectAttrs) {
        assertCanManagePlans(auth);
        if (bindingResult.hasErrors()) {
            model.addAttribute("difficultyValues", DifficultyLevel.values());
            model.addAttribute("formMode", "create");
            return "workout/form";
        }
        WorkoutPlan saved = service.createPlan(dto, auth.getName());
        redirectAttrs.addFlashAttribute("successMessage",
                "Workout plan '" + saved.getName() + "' created successfully!");
        return "redirect:/workouts/" + saved.getId();
    }

    // ------------------------------------------------------------------ //
    //  Edit                                                                 //
    // ------------------------------------------------------------------ //

    @GetMapping("/{id}/edit")
    public String editPlanForm(@PathVariable Long id, Authentication auth, Model model) {
        assertCanManagePlans(auth);
        WorkoutPlan plan = service.getEditablePlan(id, auth.getName(), isAdmin(auth));
        model.addAttribute("planDto", service.mapEntityToDto(plan));
        model.addAttribute("planId", id);
        model.addAttribute("difficultyValues", DifficultyLevel.values());
        model.addAttribute("formMode", "edit");
        return "workout/form";
    }

    @PostMapping("/{id}/edit")
    public String updatePlan(@PathVariable Long id,
                             @Valid @ModelAttribute("planDto") WorkoutPlanDto dto,
                             BindingResult bindingResult,
                             Authentication auth,
                             Model model,
                             RedirectAttributes redirectAttrs) {
        assertCanManagePlans(auth);
        if (bindingResult.hasErrors()) {
            model.addAttribute("planId", id);
            model.addAttribute("difficultyValues", DifficultyLevel.values());
            model.addAttribute("formMode", "edit");
            return "workout/form";
        }
        service.updatePlan(id, dto, auth.getName(), isAdmin(auth));
        redirectAttrs.addFlashAttribute("successMessage", "Workout plan updated successfully!");
        return "redirect:/workouts/" + id;
    }

    // ------------------------------------------------------------------ //
    //  Delete                                                               //
    // ------------------------------------------------------------------ //

    @PostMapping("/{id}/delete")
    public String deletePlan(@PathVariable Long id,
                             Authentication auth,
                             RedirectAttributes redirectAttrs) {
        assertCanManagePlans(auth);
        service.deletePlan(id, auth.getName(), isAdmin(auth));
        redirectAttrs.addFlashAttribute("successMessage", "Workout plan deleted.");
        return "redirect:/workouts";
    }

    // ------------------------------------------------------------------ //
    //  Status Transition                                                    //
    // ------------------------------------------------------------------ //

    @PostMapping("/{id}/status")
    public String changeStatus(@PathVariable Long id,
                               @RequestParam WorkoutStatus newStatus,
                               Authentication auth,
                               RedirectAttributes redirectAttrs) {
        assertCanManagePlans(auth);
        service.changeStatus(id, newStatus, auth.getName(), isAdmin(auth));
        redirectAttrs.addFlashAttribute("successMessage",
                "Status updated to " + newStatus + ".");
        return "redirect:/workouts/" + id;
    }

    // ------------------------------------------------------------------ //
    //  Helper                                                               //
    // ------------------------------------------------------------------ //

    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private boolean isTrainer(Authentication auth) {
        return auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_TRAINER"));
    }

    private void assertCanManagePlans(Authentication auth) {
        if (!isAdmin(auth) && !isTrainer(auth)) {
            throw new org.springframework.security.access.AccessDeniedException("Regular members have read-only access to published workout plans.");
        }
    }
}
