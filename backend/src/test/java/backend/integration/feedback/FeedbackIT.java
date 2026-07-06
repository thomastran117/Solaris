package backend.integration.feedback;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FeedbackIT extends AbstractIntegrationIT {

    @AfterEach
    void clean() {
        try { jdbcTemplate.execute("DELETE FROM platform_feedback"); } catch (Exception ignored) {}
    }

    private String submitBody(String category, String message, Integer rating, String pageContext) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("category", category);
        body.put("message", message);
        if (rating != null) body.put("rating", rating);
        if (pageContext != null) body.put("pageContext", pageContext);
        return objectMapper.writeValueAsString(body);
    }

    // ── POST /feedback ─────────────────────────────────────────────────────────

    @Test
    void submit_allFields_returns201() throws Exception {
        User user = createActiveUser("fb-submit@example.com", "Password1!");

        mockMvc.perform(post("/feedback")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody("BUG_REPORT",
                                "This is a detailed bug report with enough characters to pass validation.", 4, "/checkout")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.category").value("BUG_REPORT"))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.message").value("This is a detailed bug report with enough characters to pass validation."))
                .andExpect(jsonPath("$.data.rating").value(4))
                .andExpect(jsonPath("$.data.pageContext").value("/checkout"))
                .andExpect(jsonPath("$.data.submittedById").value(user.getId().toString()));

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM platform_feedback WHERE status = 'OPEN'", Integer.class);
        assertEquals(1, rows, "Feedback should be persisted");
    }

    @Test
    void submit_noOptionalFields_returns201() throws Exception {
        User user = createActiveUser("fb-minimal@example.com", "Password1!");

        mockMvc.perform(post("/feedback")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody("FEATURE_REQUEST",
                                "Please add dark mode to the application dashboard.", null, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.category").value("FEATURE_REQUEST"))
                .andExpect(jsonPath("$.data.status").value("OPEN"));
    }

    @Test
    void submit_messageTooShort_returns400() throws Exception {
        User user = createActiveUser("fb-short@example.com", "Password1!");

        mockMvc.perform(post("/feedback")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody("BUG_REPORT", "Short", null, null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submit_missingCategory_returns400() throws Exception {
        User user = createActiveUser("fb-nocat@example.com", "Password1!");

        String body = objectMapper.writeValueAsString(
                Map.of("message", "This message is definitely long enough to pass the length validation."));

        mockMvc.perform(post("/feedback")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submit_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody("BUG_REPORT",
                                "This message is long enough to pass the validation check.", 3, null)))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /feedback/mine ─────────────────────────────────────────────────────

    @Test
    void getMine_empty_returns200() throws Exception {
        User user = createActiveUser("fb-empty@example.com", "Password1!");

        mockMvc.perform(get("/feedback/mine")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.meta.totalElements").value(0));
    }

    @Test
    void getMine_withFeedback_returnsPage() throws Exception {
        User user = createActiveUser("fb-list@example.com", "Password1!");

        mockMvc.perform(post("/feedback")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody("BUG_REPORT",
                                "First bug report item with sufficient length to pass.", 3, null)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/feedback")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody("UI_UX",
                                "Second feedback item about the user interface design.", null, "/home")))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/feedback/mine")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.meta.totalElements").value(2));
    }

    @Test
    void getMine_onlyReturnsOwnFeedback() throws Exception {
        User userA = createActiveUser("fb-iso-a@example.com", "Password1!");
        User userB = createActiveUser("fb-iso-b@example.com", "Password1!");

        mockMvc.perform(post("/feedback")
                        .header("Authorization", bearer(accessTokenFor(userA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(submitBody("PERFORMANCE",
                                "User A feedback about performance issues in the application.", 2, null)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/feedback/mine")
                        .header("Authorization", bearer(accessTokenFor(userB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.meta.totalElements").value(0));
    }

    @Test
    void getMine_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/feedback/mine"))
                .andExpect(status().isUnauthorized());
    }
}
