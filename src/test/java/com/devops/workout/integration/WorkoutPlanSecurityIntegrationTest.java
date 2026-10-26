package com.devops.workout.integration;

import com.devops.workout.dto.DashboardStatsDto;
import com.devops.workout.model.WorkoutPlan;
import com.devops.workout.model.WorkoutPlan.DifficultyLevel;
import com.devops.workout.model.WorkoutPlan.WorkoutStatus;
import com.devops.workout.repository.WorkoutPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end HTTP tests (MockMvc + H2) for authorization, dashboard isolation,
 * search scoping and HTTP status codes. Runs in the normal `mvn test` phase.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkoutPlanSecurityIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private WorkoutPlanRepository repository;

    private Long alicePlanId;
    private Long alicePublishedId;
    private Long bobPlanId;

    @BeforeEach
    void seed() {
        repository.deleteAll();
        alicePlanId = save("Alice Secret Plan", "alice", WorkoutStatus.DRAFT).getId();
        alicePublishedId = save("Alice Published", "alice", WorkoutStatus.PUBLISHED).getId();
        bobPlanId = save("Bob Plan", "bob", WorkoutStatus.DRAFT).getId();
    }

    private WorkoutPlan save(String name, String owner, WorkoutStatus status) {
        return repository.save(WorkoutPlan.builder()
                .name(name).description("desc").exerciseDetails("ex")
                .durationMinutes(30).difficulty(DifficultyLevel.BEGINNER)
                .targetMuscleGroup("Legs").trainerName(owner)
                .createdBy(owner).status(status).build());
    }

    // ---- authentication ----

    @Test
    @DisplayName("anonymous users are redirected to login")
    void anonymous_redirectedToLogin() throws Exception {
        mvc.perform(get("/workouts/" + alicePlanId))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("health endpoint is public; metrics are not exposed")
    void actuator_healthPublic_metricsHidden() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/metrics")).andExpect(status().is3xxRedirection());
    }

    // ---- cross-trainer access ----

    @Test
    @WithMockUser(username = "bob", roles = "TRAINER")
    @DisplayName("trainer gets 403 viewing another trainer's plan by direct URL")
    void trainer_cannotViewOthersPlan() throws Exception {
        mvc.perform(get("/workouts/" + alicePlanId)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "bob", roles = "TRAINER")
    @DisplayName("trainer gets 403 on another trainer's edit form, update, delete and status change")
    void trainer_cannotModifyOthersPlan() throws Exception {
        mvc.perform(get("/workouts/" + alicePlanId + "/edit")).andExpect(status().isForbidden());
        mvc.perform(post("/workouts/" + alicePlanId + "/edit").with(csrf())
                        .param("name", "Hacked plan").param("description", "d")
                        .param("exerciseDetails", "e").param("durationMinutes", "10")
                        .param("difficulty", "BEGINNER").param("targetMuscleGroup", "Legs")
                        .param("trainerName", "bob"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/workouts/" + alicePlanId + "/delete").with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/workouts/" + alicePlanId + "/status").with(csrf())
                        .param("newStatus", "SUBMITTED"))
                .andExpect(status().isForbidden());

        WorkoutPlan unchanged = repository.findById(alicePlanId).orElseThrow();
        assertThat(unchanged.getName()).isEqualTo("Alice Secret Plan");
        assertThat(unchanged.getStatus()).isEqualTo(WorkoutStatus.DRAFT);
    }

    @Test
    @WithMockUser(username = "alice", roles = "TRAINER")
    @DisplayName("owner can open own plan; trainer can submit own plan (regression: used to be 403)")
    void owner_canViewAndSubmitOwnPlan() throws Exception {
        mvc.perform(get("/workouts/" + alicePlanId)).andExpect(status().isOk());
        mvc.perform(post("/workouts/" + alicePlanId + "/status").with(csrf())
                        .param("newStatus", "SUBMITTED"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/workouts/" + alicePlanId));
        assertThat(repository.findById(alicePlanId).orElseThrow().getStatus())
                .isEqualTo(WorkoutStatus.SUBMITTED);
    }

    @Test
    @WithMockUser(username = "alice", roles = "TRAINER")
    @DisplayName("trainer cannot approve own plan (403) and cannot edit own published plan (400)")
    void trainer_roleRulesEnforced() throws Exception {
        mvc.perform(post("/workouts/" + alicePlanId + "/status").with(csrf())
                        .param("newStatus", "SUBMITTED")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/workouts/" + alicePlanId + "/status").with(csrf())
                        .param("newStatus", "APPROVED")).andExpect(status().isForbidden());
        mvc.perform(get("/workouts/" + alicePublishedId + "/edit")).andExpect(status().isBadRequest());
    }

    // ---- admin ----

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN", "TRAINER"})
    @DisplayName("admin can view any plan and approve a submitted plan")
    void admin_hasFullAccess() throws Exception {
        mvc.perform(get("/workouts/" + alicePlanId)).andExpect(status().isOk());
        mvc.perform(post("/workouts/" + alicePlanId + "/status").with(csrf())
                .param("newStatus", "SUBMITTED")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/workouts/" + alicePlanId + "/status").with(csrf())
                .param("newStatus", "APPROVED")).andExpect(status().is3xxRedirection());
        assertThat(repository.findById(alicePlanId).orElseThrow().getStatus())
                .isEqualTo(WorkoutStatus.APPROVED);
    }

    // ---- search + dashboard isolation ----

    @Test
    @WithMockUser(username = "bob", roles = "TRAINER")
    @DisplayName("trainer keyword search never returns another trainer's plans")
    void search_isScopedToOwner() throws Exception {
        mvc.perform(get("/workouts").param("keyword", "Alice"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("plans", org.hamcrest.Matchers.empty()));
        mvc.perform(get("/workouts").param("keyword", "Bob"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("plans", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    @WithMockUser(username = "bob", roles = "TRAINER")
    @DisplayName("trainer dashboard shows only own statistics and recent plans")
    void dashboard_isolatedForTrainer() throws Exception {
        DashboardStatsDto stats = (DashboardStatsDto) mvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("recentPlans", org.hamcrest.Matchers.hasSize(1)))
                .andReturn().getModelAndView().getModel().get("stats");
        assertThat(stats.getTotalPlans()).isEqualTo(1L);
        assertThat(stats.getPublishedCount()).isZero();
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN", "TRAINER"})
    @DisplayName("admin dashboard and search cover all plans")
    void dashboard_adminSeesEverything() throws Exception {
        DashboardStatsDto stats = (DashboardStatsDto) mvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andReturn().getModelAndView().getModel().get("stats");
        assertThat(stats.getTotalPlans()).isEqualTo(3L);
        mvc.perform(get("/workouts").param("keyword", "Alice"))
                .andExpect(model().attribute("plans", org.hamcrest.Matchers.hasSize(2)));
    }

    // ---- status codes ----

    @Test
    @WithMockUser(username = "alice", roles = "TRAINER")
    @DisplayName("unknown plan -> 404, malformed id -> 400, invalid filter value -> 400")
    void errorStatusCodes() throws Exception {
        mvc.perform(get("/workouts/999999")).andExpect(status().isNotFound());
        mvc.perform(get("/workouts/not-a-number")).andExpect(status().isBadRequest());
        mvc.perform(get("/workouts").param("status", "NOPE")).andExpect(status().isBadRequest());
        assertThat(bobPlanId).isNotNull();
    }

    @Test
    @WithMockUser(username = "alice", roles = "TRAINER")
    @DisplayName("invalid form input is re-displayed (200) and nothing is persisted")
    void createPlan_validationFailure() throws Exception {
        long before = repository.count();
        mvc.perform(post("/workouts/new").with(csrf())
                        .param("name", "x").param("description", "").param("durationMinutes", "0"))
                .andExpect(status().isOk());
        assertThat(repository.count()).isEqualTo(before);
    }
}
