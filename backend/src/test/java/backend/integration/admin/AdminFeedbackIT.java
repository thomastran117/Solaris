package backend.integration.admin;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.PlatformFeedback;
import backend.models.core.User;
import backend.models.enums.FeedbackCategory;
import backend.models.enums.FeedbackStatus;
import backend.models.enums.UserRole;
import backend.models.enums.UserStatus;
import backend.repositories.PlatformFeedbackRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminFeedbackIT extends AbstractIntegrationIT {

    @Autowired private PlatformFeedbackRepository feedbackRepository;

    @AfterEach
    void clean() {
        try { jdbcTemplate.execute("DELETE FROM platform_feedback"); } catch (Exception ignored) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User createAdmin(String email) {
        User u = new User();
        u.setEmail(email);
        u.setPassword(passwordEncoder.encode("Password1!"));
        u.setRole(UserRole.ADMIN);
        u.setStatus(UserStatus.ACTIVE);
        return userRepository.save(u);
    }

    private PlatformFeedback createFeedback(User submitter, FeedbackCategory category, FeedbackStatus status) {
        PlatformFeedback f = new PlatformFeedback();
        f.setSubmittedBy(submitter);
        f.setCategory(category);
        f.setMessage("Test feedback message long enough to satisfy the minimum constraint.");
        f.setStatus(status);
        return feedbackRepository.save(f);
    }

    // ── GET /admin/feedback ───────────────────────────────────────────────────

    @Test
    void listAll_empty_returns200() throws Exception {
        User admin = createAdmin("af-list-empty@example.com");

        mockMvc.perform(get("/admin/feedback")
                        .header("Authorization", bearer(accessTokenFor(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.meta.totalElements").value(0));
    }

    @Test
    void listAll_returnsAllFeedback() throws Exception {
        User admin = createAdmin("af-list-all@example.com");
        User submitter = createActiveUser("af-submitter@example.com", "Password1!");
        createFeedback(submitter, FeedbackCategory.BUG_REPORT, FeedbackStatus.OPEN);
        createFeedback(submitter, FeedbackCategory.UI_UX, FeedbackStatus.RESOLVED);

        mockMvc.perform(get("/admin/feedback")
                        .header("Authorization", bearer(accessTokenFor(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.meta.totalElements").value(2));
    }

    @Test
    void listAll_filterByStatus_returnsMatchingOnly() throws Exception {
        User admin = createAdmin("af-filter-status@example.com");
        User submitter = createActiveUser("af-filter-sub@example.com", "Password1!");
        createFeedback(submitter, FeedbackCategory.BUG_REPORT, FeedbackStatus.OPEN);
        createFeedback(submitter, FeedbackCategory.UI_UX, FeedbackStatus.RESOLVED);

        mockMvc.perform(get("/admin/feedback")
                        .param("status", "OPEN")
                        .header("Authorization", bearer(accessTokenFor(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].status").value("OPEN"))
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }

    @Test
    void listAll_filterByCategory_returnsMatchingOnly() throws Exception {
        User admin = createAdmin("af-filter-cat@example.com");
        User submitter = createActiveUser("af-filter-cat-sub@example.com", "Password1!");
        createFeedback(submitter, FeedbackCategory.BUG_REPORT, FeedbackStatus.OPEN);
        createFeedback(submitter, FeedbackCategory.FEATURE_REQUEST, FeedbackStatus.OPEN);

        mockMvc.perform(get("/admin/feedback")
                        .param("category", "BUG_REPORT")
                        .header("Authorization", bearer(accessTokenFor(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].category").value("BUG_REPORT"))
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }

    @Test
    void listAll_nonAdmin_returns403() throws Exception {
        User user = createActiveUser("af-nonadmin@example.com", "Password1!");

        mockMvc.perform(get("/admin/feedback")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listAll_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/admin/feedback"))
                .andExpect(status().isUnauthorized());
    }

    // ── PATCH /admin/feedback/{id}/status ─────────────────────────────────────

    @Test
    void updateStatus_toUnderReview_returns200() throws Exception {
        User admin = createAdmin("af-update-status@example.com");
        User submitter = createActiveUser("af-update-sub@example.com", "Password1!");
        PlatformFeedback feedback = createFeedback(submitter, FeedbackCategory.BUG_REPORT, FeedbackStatus.OPEN);

        mockMvc.perform(patch("/admin/feedback/" + feedback.getId() + "/status")
                        .header("Authorization", bearer(accessTokenFor(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "UNDER_REVIEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.data.reviewedById").value(admin.getId().toString()));

        PlatformFeedback updated = feedbackRepository.findById(feedback.getId()).orElseThrow();
        assertEquals(FeedbackStatus.UNDER_REVIEW, updated.getStatus());
        assertEquals(admin.getId(), updated.getReviewedById());
    }

    @Test
    void updateStatus_toResolved_returns200() throws Exception {
        User admin = createAdmin("af-resolve@example.com");
        User submitter = createActiveUser("af-resolve-sub@example.com", "Password1!");
        PlatformFeedback feedback = createFeedback(submitter, FeedbackCategory.FEATURE_REQUEST, FeedbackStatus.OPEN);

        mockMvc.perform(patch("/admin/feedback/" + feedback.getId() + "/status")
                        .header("Authorization", bearer(accessTokenFor(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "RESOLVED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"));

        assertEquals(FeedbackStatus.RESOLVED,
                feedbackRepository.findById(feedback.getId()).orElseThrow().getStatus());
    }

    @Test
    void updateStatus_unknownFeedback_returns404() throws Exception {
        User admin = createAdmin("af-404@example.com");

        mockMvc.perform(patch("/admin/feedback/" + UUID.randomUUID() + "/status")
                        .header("Authorization", bearer(accessTokenFor(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "RESOLVED"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateStatus_missingStatus_returns400() throws Exception {
        User admin = createAdmin("af-badreq@example.com");
        User submitter = createActiveUser("af-badreq-sub@example.com", "Password1!");
        PlatformFeedback feedback = createFeedback(submitter, FeedbackCategory.OTHER, FeedbackStatus.OPEN);

        mockMvc.perform(patch("/admin/feedback/" + feedback.getId() + "/status")
                        .header("Authorization", bearer(accessTokenFor(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateStatus_nonAdmin_returns403() throws Exception {
        User user = createActiveUser("af-nonadmin-patch@example.com", "Password1!");
        User submitter = createActiveUser("af-nonadmin-sub@example.com", "Password1!");
        PlatformFeedback feedback = createFeedback(submitter, FeedbackCategory.OTHER, FeedbackStatus.OPEN);

        mockMvc.perform(patch("/admin/feedback/" + feedback.getId() + "/status")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "RESOLVED"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateStatus_unauthenticated_returns401() throws Exception {
        mockMvc.perform(patch("/admin/feedback/" + UUID.randomUUID() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "RESOLVED"))))
                .andExpect(status().isUnauthorized());
    }
}
