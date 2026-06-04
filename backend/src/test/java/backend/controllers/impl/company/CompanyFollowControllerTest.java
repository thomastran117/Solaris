package backend.controllers.impl.company;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.responses.company.CompanyFollowResponse;
import backend.dtos.responses.company.FollowStatusResponse;
import backend.dtos.responses.company.FollowedCompanyResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.services.intf.company.CompanyFollowService;
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

class CompanyFollowControllerTest {

    private CompanyFollowService followService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final UUID USER_ID    = TestIds.uuid(1);
    private static final UUID COMPANY_ID = TestIds.uuid(2);
    private static final UUID FOLLOW_ID  = TestIds.uuid(3);

    @BeforeEach
    void setUp() {
        followService = mock(CompanyFollowService.class);
        CompanyFollowController controller = new CompanyFollowController(followService);
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

    // ─── POST /companies/{id}/follow ─────────────────────────────────────────

    @Test
    void follow_authenticated_returns200WithFollowResponse() throws Exception {
        authenticateAs(USER_ID);
        when(followService.follow(USER_ID, COMPANY_ID)).thenReturn(makeFollowResponse());

        mockMvc.perform(post("/companies/" + COMPANY_ID + "/follow"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(FOLLOW_ID.toString()))
                .andExpect(jsonPath("$.companyId").value(COMPANY_ID.toString()));
    }

    @Test
    void follow_alreadyFollowing_returns409() throws Exception {
        authenticateAs(USER_ID);
        when(followService.follow(any(), any()))
                .thenThrow(new ConflictException("Already following this company"));

        mockMvc.perform(post("/companies/" + COMPANY_ID + "/follow"))
                .andExpect(status().isConflict());
    }

    // ─── DELETE /companies/{id}/follow ───────────────────────────────────────

    @Test
    void unfollow_authenticated_returns204() throws Exception {
        authenticateAs(USER_ID);

        mockMvc.perform(delete("/companies/" + COMPANY_ID + "/follow"))
                .andExpect(status().isNoContent());

        verify(followService).unfollow(USER_ID, COMPANY_ID);
    }

    // ─── GET /companies/{id}/follow/status ───────────────────────────────────

    @Test
    void getStatus_anonymous_returnsFollowerCountOnly() throws Exception {
        when(followService.getStatus(isNull(), eq(COMPANY_ID)))
                .thenReturn(new FollowStatusResponse(false, false, 7L));

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/follow/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.following").value(false))
                .andExpect(jsonPath("$.followerCount").value(7));
    }

    @Test
    void getStatus_authenticated_returnsPersonalStatus() throws Exception {
        authenticateAs(USER_ID);
        when(followService.getStatus(USER_ID, COMPANY_ID))
                .thenReturn(new FollowStatusResponse(true, true, 10L));

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/follow/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.following").value(true))
                .andExpect(jsonPath("$.notificationsEnabled").value(true))
                .andExpect(jsonPath("$.followerCount").value(10));
    }

    // ─── PATCH /companies/{id}/follow ────────────────────────────────────────

    @Test
    void patchFollow_validBody_returns200() throws Exception {
        authenticateAs(USER_ID);
        when(followService.setNotifications(USER_ID, COMPANY_ID, false))
                .thenReturn(makeFollowResponse());

        mockMvc.perform(patch("/companies/" + COMPANY_ID + "/follow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("notificationsEnabled", false))))
                .andExpect(status().isOk());
    }

    @Test
    void patchFollow_notFollowing_returns404() throws Exception {
        authenticateAs(USER_ID);
        when(followService.setNotifications(any(), any(), anyBoolean()))
                .thenThrow(new ResourceNotFoundException("Not following this company"));

        mockMvc.perform(patch("/companies/" + COMPANY_ID + "/follow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("notificationsEnabled", false))))
                .andExpect(status().isNotFound());
    }

    // ─── GET /me/following ───────────────────────────────────────────────────

    @Test
    void listFollowing_authenticated_returns200WithPagedList() throws Exception {
        authenticateAs(USER_ID);
        FollowedCompanyResponse item = new FollowedCompanyResponse(
                FOLLOW_ID, COMPANY_ID, "Test Co", null, "Tech", true, Instant.now());
        when(followService.listFollowing(USER_ID, 0, 20))
                .thenReturn(new PagedResponse<>(new PageImpl<>(List.of(item), PageRequest.of(0, 20), 1)));

        mockMvc.perform(get("/me/following"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].companyId").value(COMPANY_ID.toString()));
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private CompanyFollowResponse makeFollowResponse() {
        return new CompanyFollowResponse(FOLLOW_ID, COMPANY_ID, Instant.now(), true);
    }

    private static final class NoOpValidator implements Validator {
        @Override public boolean supports(Class<?> clazz) { return true; }
        @Override public void validate(Object target, Errors errors) {}
    }
}
