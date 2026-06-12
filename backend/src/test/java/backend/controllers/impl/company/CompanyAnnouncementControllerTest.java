package backend.controllers.impl.company;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.responses.company.AnnouncementResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.services.intf.company.CompanyAnnouncementService;
import backend.testutil.TestIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CompanyAnnouncementControllerTest {

    private CompanyAnnouncementService announcementService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final UUID USER_ID         = TestIds.uuid(1);
    private static final UUID COMPANY_ID      = TestIds.uuid(2);
    private static final UUID ANNOUNCEMENT_ID = TestIds.uuid(3);

    @BeforeEach
    void setUp() {
        announcementService = mock(CompanyAnnouncementService.class);
        CompanyAnnouncementController controller = new CompanyAnnouncementController(announcementService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new NoOpValidator())
                .defaultRequest(get("/").accept(MediaType.APPLICATION_JSON))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ─── POST /companies/{id}/announcements ───────────────────────────────────

    @Test
    void create_validRequest_returns201() throws Exception {
        authenticateAs(USER_ID);
        when(announcementService.create(eq(COMPANY_ID), eq(USER_ID), any()))
                .thenReturn(makeAnnouncementResponse("DRAFT"));

        mockMvc.perform(post("/companies/" + COMPANY_ID + "/announcements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "New arrivals",
                                "body", "Check out our latest collection.",
                                "type", "NEW_COLLECTION"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ANNOUNCEMENT_ID.toString()))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void create_forbidden_returns403() throws Exception {
        authenticateAs(USER_ID);
        when(announcementService.create(any(), any(), any()))
                .thenThrow(new ForbiddenException("Insufficient permissions"));

        mockMvc.perform(post("/companies/" + COMPANY_ID + "/announcements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "Test", "body", "Body", "type", "GENERAL"))))
                .andExpect(status().isForbidden());
    }

    // ─── GET /companies/{id}/announcements ────────────────────────────────────

    @Test
    void listPublished_public_returns200WithPagedAnnouncements() throws Exception {
        AnnouncementResponse ann = makeAnnouncementResponse("PUBLISHED");
        when(announcementService.listPublished(eq(COMPANY_ID), anyInt(), anyInt()))
                .thenReturn(new PagedResponse<>(new PageImpl<>(List.of(ann), PageRequest.of(0, 20), 1)));

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/announcements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(ANNOUNCEMENT_ID.toString()))
                .andExpect(jsonPath("$.items[0].status").value("PUBLISHED"));
    }

    @Test
    void listPublished_noAuthRequired_returns200() throws Exception {
        when(announcementService.listPublished(any(), anyInt(), anyInt()))
                .thenReturn(new PagedResponse<>(new PageImpl<>(List.of())));

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/announcements"))
                .andExpect(status().isOk());
    }

    // ─── GET /companies/{id}/announcements/all ────────────────────────────────

    @Test
    void listAll_authenticated_returns200() throws Exception {
        authenticateAs(USER_ID);
        when(announcementService.listAll(eq(COMPANY_ID), eq(USER_ID), anyInt(), anyInt()))
                .thenReturn(new PagedResponse<>(new PageImpl<>(List.of())));

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/announcements/all"))
                .andExpect(status().isOk());
    }

    // ─── PATCH /companies/{id}/announcements/{annId} ─────────────────────────

    @Test
    void update_validRequest_returns200WithUpdatedResponse() throws Exception {
        authenticateAs(USER_ID);
        when(announcementService.update(eq(COMPANY_ID), eq(ANNOUNCEMENT_ID), eq(USER_ID), any()))
                .thenReturn(makeAnnouncementResponse("PUBLISHED"));

        mockMvc.perform(patch("/companies/" + COMPANY_ID + "/announcements/" + ANNOUNCEMENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("publish", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void update_notFound_returns404() throws Exception {
        authenticateAs(USER_ID);
        when(announcementService.update(any(), any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("Announcement not found"));

        mockMvc.perform(patch("/companies/" + COMPANY_ID + "/announcements/" + ANNOUNCEMENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_forbidden_returns403() throws Exception {
        authenticateAs(USER_ID);
        when(announcementService.update(any(), any(), any(), any()))
                .thenThrow(new ForbiddenException("Insufficient permissions"));

        mockMvc.perform(patch("/companies/" + COMPANY_ID + "/announcements/" + ANNOUNCEMENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    // ─── DELETE /companies/{id}/announcements/{annId} ────────────────────────

    @Test
    void delete_draft_returns204() throws Exception {
        authenticateAs(USER_ID);

        mockMvc.perform(delete("/companies/" + COMPANY_ID + "/announcements/" + ANNOUNCEMENT_ID))
                .andExpect(status().isNoContent());

        verify(announcementService).delete(COMPANY_ID, ANNOUNCEMENT_ID, USER_ID);
    }

    @Test
    void delete_published_returns400() throws Exception {
        authenticateAs(USER_ID);
        doThrow(new BadRequestException("Cannot delete a published announcement"))
                .when(announcementService).delete(any(), any(), any());

        mockMvc.perform(delete("/companies/" + COMPANY_ID + "/announcements/" + ANNOUNCEMENT_ID))
                .andExpect(status().isBadRequest());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private AnnouncementResponse makeAnnouncementResponse(String status) {
        return new AnnouncementResponse(
                ANNOUNCEMENT_ID, COMPANY_ID, "Test Company", null,
                "Test Announcement", "Body text", "GENERAL", status,
                null, "PUBLISHED".equals(status) ? Instant.now() : null,
                Instant.now(), Instant.now());
    }

    private static final class NoOpValidator implements Validator {
        @Override public boolean supports(Class<?> clazz) { return true; }
        @Override public void validate(Object target, Errors errors) {}
    }
}
