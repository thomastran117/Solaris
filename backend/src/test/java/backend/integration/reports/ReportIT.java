package backend.integration.reports;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Covers ReportController (POST /reports) — @RequireAuth.
 *
 * Report uses USER target type exclusively so no extra entities (products,
 * companies) are needed beyond a second User row for the target.
 *
 * reporterId and targetId are stored as bare UUIDs (no FK to users), so
 * cleanup just deletes reports (cascades to report_screenshots).
 */
class ReportIT extends AbstractIntegrationIT {

    @AfterEach
    void clean() {
        try { jdbcTemplate.execute("DELETE FROM report_screenshots"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM reports"); } catch (Exception ignored) {}
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String reportBody(String targetType, String targetId,
                              String reason, String title, String description) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("targetType", targetType);
        body.put("targetId", targetId);
        body.put("reason", reason);
        body.put("title", title);
        body.put("description", description);
        return objectMapper.writeValueAsString(body);
    }

    // ── POST /reports ──────────────────────────────────────────────────────────

    @Test
    void createReport_reportUser_returns201() throws Exception {
        User reporter = createActiveUser("rep-reporter@example.com", "Password1!");
        User target   = createActiveUser("rep-target@example.com",   "Password1!");

        mockMvc.perform(post("/reports")
                        .header("Authorization", bearer(accessTokenFor(reporter)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportBody("USER", target.getId().toString(),
                                "HARASSMENT", "Harassing me in reviews",
                                "This user has been repeatedly harassing me in product reviews.")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.targetType").value("USER"))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.reason").value("HARASSMENT"));
    }

    @Test
    void createReport_selfReport_returns400() throws Exception {
        User reporter = createActiveUser("rep-self@example.com", "Password1!");

        mockMvc.perform(post("/reports")
                        .header("Authorization", bearer(accessTokenFor(reporter)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportBody("USER", reporter.getId().toString(),
                                "OTHER", "Reporting myself", "Attempting to report myself, which should be rejected.")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReport_targetUserNotFound_returns404() throws Exception {
        User reporter = createActiveUser("rep-notfound@example.com", "Password1!");

        mockMvc.perform(post("/reports")
                        .header("Authorization", bearer(accessTokenFor(reporter)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportBody("USER", UUID.randomUUID().toString(),
                                "SPAM", "Fake user account",
                                "This account appears to be a bot or entirely fake profile.")))
                .andExpect(status().isNotFound());
    }

    @Test
    void createReport_duplicate_returns409() throws Exception {
        User reporter = createActiveUser("rep-dup@example.com",        "Password1!");
        User target   = createActiveUser("rep-dup-target@example.com", "Password1!");
        String body = reportBody("USER", target.getId().toString(),
                "FRAUD", "Fraudulent seller",
                "This user is selling counterfeit or fraudulent products.");

        mockMvc.perform(post("/reports")
                        .header("Authorization", bearer(accessTokenFor(reporter)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        // Identical report from the same reporter → 409
        mockMvc.perform(post("/reports")
                        .header("Authorization", bearer(accessTokenFor(reporter)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void createReport_titleTooShort_returns400() throws Exception {
        User reporter = createActiveUser("rep-short@example.com",        "Password1!");
        User target   = createActiveUser("rep-short-target@example.com", "Password1!");

        // title @Size(min=5) — "Bad" is 3 chars
        mockMvc.perform(post("/reports")
                        .header("Authorization", bearer(accessTokenFor(reporter)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportBody("USER", target.getId().toString(),
                                "SPAM", "Bad",
                                "This is a long enough description to pass the minimum length check.")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReport_missingReason_returns400() throws Exception {
        User reporter = createActiveUser("rep-noreason@example.com",        "Password1!");
        User target   = createActiveUser("rep-noreason-target@example.com", "Password1!");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("targetType", "USER");
        body.put("targetId", target.getId().toString());
        body.put("title", "Valid report title here");
        body.put("description", "Valid description that is definitely long enough to pass validation.");
        // reason omitted → @NotNull fails

        mockMvc.perform(post("/reports")
                        .header("Authorization", bearer(accessTokenFor(reporter)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReport_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reportBody("USER", UUID.randomUUID().toString(),
                                "OTHER", "Test title here",
                                "Test description here that is long enough to pass validation.")))
                .andExpect(status().isUnauthorized());
    }
}
