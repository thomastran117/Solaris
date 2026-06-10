package backend.integration.admin;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.Report;
import backend.models.core.User;
import backend.models.enums.ReportReason;
import backend.models.enums.ReportStatus;
import backend.models.enums.ReportTargetType;
import backend.models.enums.UserRole;
import backend.models.enums.UserStatus;
import backend.repositories.ReportRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Covers AdminReportController (/admin/reports) — ADMIN-only.
 *
 * search: ReportSearchServiceImpl falls back to an empty PagedResponse when
 * elasticsearchOperations (mocked) returns null, so /search always returns
 * 200 with empty data in the test environment.
 *
 * reindex: IndexVersionManager.rolloverIndex is a no-op when
 * app.elasticsearch.versioning.enabled=false, and reportIndexingService.reindexAll()
 * just queues async tasks — both return immediately without contacting ES.
 */
class AdminReportControllerIT extends AbstractIntegrationIT {

    @Autowired private ReportRepository reportRepository;

    @AfterEach
    void clean() {
        try { jdbcTemplate.execute("DELETE FROM report_screenshots"); } catch (Exception ignored) {}
        try { jdbcTemplate.execute("DELETE FROM reports"); } catch (Exception ignored) {}
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

    private Report createReport(UUID reporterId, ReportTargetType targetType, ReportStatus status) {
        Report r = new Report();
        r.setTargetType(targetType);
        r.setTargetId(UUID.randomUUID());
        r.setReporterId(reporterId);
        r.setReason(ReportReason.SPAM);
        r.setTitle("Test report");
        r.setDescription("Test description");
        r.setStatus(status);
        return reportRepository.save(r);
    }

    private String resolutionBody(String resolution) throws Exception {
        return objectMapper.writeValueAsString(Map.of("resolution", resolution));
    }

    // ── GET /admin/reports ──────────────────────────────────────────────────────

    @Test
    void listReports_empty_returnsEmptyPage() throws Exception {
        User admin = createAdmin("ar-admin-list-empty@example.com");

        mockMvc.perform(get("/admin/reports")
                        .header("Authorization", bearer(accessTokenFor(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.meta.totalElements").value(0));
    }

    @Test
    void listReports_withReports_returnsPagedResults() throws Exception {
        User admin = createAdmin("ar-admin-list@example.com");
        User reporter = createActiveUser("ar-reporter-list@example.com", "Password1!");
        createReport(reporter.getId(), ReportTargetType.PRODUCT, ReportStatus.OPEN);
        createReport(reporter.getId(), ReportTargetType.REVIEW, ReportStatus.OPEN);

        mockMvc.perform(get("/admin/reports")
                        .header("Authorization", bearer(accessTokenFor(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.meta.totalElements").value(2));
    }

    @Test
    void listReports_filterByStatus_returnsMatching() throws Exception {
        User admin = createAdmin("ar-admin-list-status@example.com");
        User reporter = createActiveUser("ar-reporter-list-status@example.com", "Password1!");
        createReport(reporter.getId(), ReportTargetType.PRODUCT, ReportStatus.OPEN);
        createReport(reporter.getId(), ReportTargetType.PRODUCT, ReportStatus.ACTIONED);

        mockMvc.perform(get("/admin/reports")
                        .header("Authorization", bearer(accessTokenFor(admin)))
                        .param("status", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].status").value("OPEN"))
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }

    @Test
    void listReports_nonAdmin_returns403() throws Exception {
        User user = createActiveUser("ar-user-list-403@example.com", "Password1!");

        mockMvc.perform(get("/admin/reports")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listReports_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/admin/reports"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /admin/reports/{reportId} ───────────────────────────────────────────

    @Test
    void getReport_existing_returns200WithFields() throws Exception {
        User admin = createAdmin("ar-admin-get@example.com");
        User reporter = createActiveUser("ar-reporter-get@example.com", "Password1!");
        Report report = createReport(reporter.getId(), ReportTargetType.COMPANY, ReportStatus.OPEN);

        mockMvc.perform(get("/admin/reports/" + report.getId())
                        .header("Authorization", bearer(accessTokenFor(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(report.getId().toString()))
                .andExpect(jsonPath("$.data.targetType").value("COMPANY"))
                .andExpect(jsonPath("$.data.reason").value("SPAM"))
                .andExpect(jsonPath("$.data.title").value("Test report"))
                .andExpect(jsonPath("$.data.description").value("Test description"))
                .andExpect(jsonPath("$.data.status").value("OPEN"));
    }

    @Test
    void getReport_unknown_returns404() throws Exception {
        User admin = createAdmin("ar-admin-get-404@example.com");

        mockMvc.perform(get("/admin/reports/" + UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(admin))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getReport_nonAdmin_returns403() throws Exception {
        User user = createActiveUser("ar-user-get-403@example.com", "Password1!");

        mockMvc.perform(get("/admin/reports/" + UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getReport_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/admin/reports/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ── PATCH /admin/reports/{reportId} ─────────────────────────────────────────

    @Test
    void resolve_actioned_returns204AndPersistsStatus() throws Exception {
        User admin = createAdmin("ar-admin-resolve-actioned@example.com");
        User reporter = createActiveUser("ar-reporter-resolve-actioned@example.com", "Password1!");
        Report report = createReport(reporter.getId(), ReportTargetType.PRODUCT, ReportStatus.OPEN);

        mockMvc.perform(patch("/admin/reports/" + report.getId())
                        .header("Authorization", bearer(accessTokenFor(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resolutionBody("ACTIONED")))
                .andExpect(status().isNoContent());

        Report updated = reportRepository.findById(report.getId()).orElseThrow();
        assertEquals(ReportStatus.ACTIONED, updated.getStatus());
        assertEquals(admin.getId(), updated.getResolvedBy());
        assertNotNull(updated.getResolvedAt());
    }

    @Test
    void resolve_dismissed_returns204AndPersistsStatus() throws Exception {
        User admin = createAdmin("ar-admin-resolve-dismissed@example.com");
        User reporter = createActiveUser("ar-reporter-resolve-dismissed@example.com", "Password1!");
        Report report = createReport(reporter.getId(), ReportTargetType.PRODUCT, ReportStatus.OPEN);

        mockMvc.perform(patch("/admin/reports/" + report.getId())
                        .header("Authorization", bearer(accessTokenFor(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resolutionBody("DISMISSED")))
                .andExpect(status().isNoContent());

        Report updated = reportRepository.findById(report.getId()).orElseThrow();
        assertEquals(ReportStatus.DISMISSED, updated.getStatus());
        assertNotNull(updated.getResolvedAt());
    }

    @Test
    void resolve_alreadyResolved_returns400() throws Exception {
        User admin = createAdmin("ar-admin-resolve-already@example.com");
        User reporter = createActiveUser("ar-reporter-resolve-already@example.com", "Password1!");
        Report report = createReport(reporter.getId(), ReportTargetType.PRODUCT, ReportStatus.ACTIONED);

        mockMvc.perform(patch("/admin/reports/" + report.getId())
                        .header("Authorization", bearer(accessTokenFor(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resolutionBody("DISMISSED")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resolve_resolutionOpen_returns400() throws Exception {
        User admin = createAdmin("ar-admin-resolve-open@example.com");
        User reporter = createActiveUser("ar-reporter-resolve-open@example.com", "Password1!");
        Report report = createReport(reporter.getId(), ReportTargetType.PRODUCT, ReportStatus.OPEN);

        mockMvc.perform(patch("/admin/reports/" + report.getId())
                        .header("Authorization", bearer(accessTokenFor(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resolutionBody("OPEN")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resolve_unknownReport_returns404() throws Exception {
        User admin = createAdmin("ar-admin-resolve-404@example.com");

        mockMvc.perform(patch("/admin/reports/" + UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resolutionBody("ACTIONED")))
                .andExpect(status().isNotFound());
    }

    @Test
    void resolve_missingResolutionField_returns400() throws Exception {
        User admin = createAdmin("ar-admin-resolve-missing@example.com");

        mockMvc.perform(patch("/admin/reports/" + UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resolve_nonAdmin_returns403() throws Exception {
        User user = createActiveUser("ar-user-resolve-403@example.com", "Password1!");

        mockMvc.perform(patch("/admin/reports/" + UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resolutionBody("ACTIONED")))
                .andExpect(status().isForbidden());
    }

    @Test
    void resolve_unauthenticated_returns401() throws Exception {
        mockMvc.perform(patch("/admin/reports/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resolutionBody("ACTIONED")))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /admin/reports/search ───────────────────────────────────────────────

    @Test
    void search_returns200WithEmptyResults() throws Exception {
        User admin = createAdmin("ar-admin-search@example.com");

        mockMvc.perform(get("/admin/reports/search")
                        .header("Authorization", bearer(accessTokenFor(admin)))
                        .param("q", "spam"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void search_nonAdmin_returns403() throws Exception {
        User user = createActiveUser("ar-user-search-403@example.com", "Password1!");

        mockMvc.perform(get("/admin/reports/search")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isForbidden());
    }

    @Test
    void search_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/admin/reports/search"))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /admin/reports/reindex ─────────────────────────────────────────────

    @Test
    void reindex_admin_returns200() throws Exception {
        User admin = createAdmin("ar-admin-reindex@example.com");

        mockMvc.perform(post("/admin/reports/reindex")
                        .header("Authorization", bearer(accessTokenFor(admin))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("Report index rollover and full reindex triggered"));
    }

    @Test
    void reindex_nonAdmin_returns403() throws Exception {
        User user = createActiveUser("ar-user-reindex-403@example.com", "Password1!");

        mockMvc.perform(post("/admin/reports/reindex")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isForbidden());
    }

    @Test
    void reindex_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/admin/reports/reindex"))
                .andExpect(status().isUnauthorized());
    }
}
