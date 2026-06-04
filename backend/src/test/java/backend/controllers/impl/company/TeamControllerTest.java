package backend.controllers.impl.company;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.responses.company.CompanyRoleResponse;
import backend.dtos.responses.company.TeamInvitePreviewResponse;
import backend.dtos.responses.company.TeamMembershipResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.enums.CompanyCapability;
import backend.models.enums.CompanyMembershipStatus;
import backend.models.enums.CompanyRole;
import backend.services.intf.company.CompanyMembershipService;
import backend.testutil.TestIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TeamControllerTest {

    private CompanyMembershipService membershipService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final UUID USER_ID       = TestIds.uuid(1);
    private static final UUID COMPANY_ID    = TestIds.uuid(2);
    private static final UUID MEMBERSHIP_ID = TestIds.uuid(3);
    private static final UUID MEMBER_ID     = TestIds.uuid(4);

    @BeforeEach
    void setUp() {
        membershipService = mock(CompanyMembershipService.class);
        TeamController controller = new TeamController(membershipService);

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

    // ─── POST /companies/{companyId}/team/invites ─────────────────────────────

    @Test
    void invite_returns201() throws Exception {
        authenticateAs(USER_ID);
        TeamMembershipResponse resp = makeMembershipResponse(MEMBERSHIP_ID, MEMBER_ID);
        when(membershipService.inviteMember(eq(COMPANY_ID), eq(USER_ID), any())).thenReturn(resp);

        mockMvc.perform(post("/companies/" + COMPANY_ID + "/team/invites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "dev@test.com", "role", "EMPLOYEE"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(MEMBERSHIP_ID.toString()));
    }

    @Test
    void invite_ownerRole_returns400() throws Exception {
        authenticateAs(USER_ID);
        when(membershipService.inviteMember(any(), any(), any()))
                .thenThrow(new BadRequestException("Cannot invite a user as OWNER"));

        mockMvc.perform(post("/companies/" + COMPANY_ID + "/team/invites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "owner@test.com", "role", "OWNER"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invite_conflict_returns409() throws Exception {
        authenticateAs(USER_ID);
        when(membershipService.inviteMember(any(), any(), any()))
                .thenThrow(new ConflictException("An invite for this email is already pending"));

        mockMvc.perform(post("/companies/" + COMPANY_ID + "/team/invites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "dup@test.com", "role", "EMPLOYEE"))))
                .andExpect(status().isConflict());
    }

    @Test
    void invite_forbidden_returns403() throws Exception {
        authenticateAs(USER_ID);
        when(membershipService.inviteMember(any(), any(), any()))
                .thenThrow(new ForbiddenException("Your role does not allow this action"));

        mockMvc.perform(post("/companies/" + COMPANY_ID + "/team/invites")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("email", "x@test.com", "role", "EMPLOYEE"))))
                .andExpect(status().isForbidden());
    }

    // ─── GET /companies/{companyId}/team ──────────────────────────────────────

    @Test
    void listMembers_returns200WithList() throws Exception {
        authenticateAs(USER_ID);
        TeamMembershipResponse resp = makeMembershipResponse(MEMBERSHIP_ID, MEMBER_ID);
        when(membershipService.listMembers(COMPANY_ID, USER_ID)).thenReturn(List.of(resp));

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/team"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(MEMBERSHIP_ID.toString()));
    }

    @Test
    void listMembers_forbidden_returns403() throws Exception {
        authenticateAs(USER_ID);
        when(membershipService.listMembers(any(), any()))
                .thenThrow(new ForbiddenException("Access denied"));

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/team"))
                .andExpect(status().isForbidden());
    }

    // ─── PATCH /companies/{companyId}/team/{membershipId} ────────────────────

    @Test
    void updateRole_returns200() throws Exception {
        authenticateAs(USER_ID);
        TeamMembershipResponse resp = makeMembershipResponse(MEMBERSHIP_ID, MEMBER_ID);
        when(membershipService.updateMemberRole(eq(COMPANY_ID), eq(MEMBERSHIP_ID), eq(USER_ID), any()))
                .thenReturn(resp);

        mockMvc.perform(patch("/companies/" + COMPANY_ID + "/team/" + MEMBERSHIP_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MANAGER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(MEMBERSHIP_ID.toString()));
    }

    @Test
    void updateRole_targetIsOwner_returns409() throws Exception {
        authenticateAs(USER_ID);
        when(membershipService.updateMemberRole(any(), any(), any(), any()))
                .thenThrow(new ConflictException("Cannot change the role of the company owner"));

        mockMvc.perform(patch("/companies/" + COMPANY_ID + "/team/" + MEMBERSHIP_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"MANAGER\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void updateRole_notFound_returns404() throws Exception {
        authenticateAs(USER_ID);
        when(membershipService.updateMemberRole(any(), any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("Team member not found"));

        mockMvc.perform(patch("/companies/" + COMPANY_ID + "/team/" + MEMBERSHIP_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"EMPLOYEE\"}"))
                .andExpect(status().isNotFound());
    }

    // ─── DELETE /companies/{companyId}/team/{membershipId} ───────────────────

    @Test
    void revoke_returns204() throws Exception {
        authenticateAs(USER_ID);

        mockMvc.perform(delete("/companies/" + COMPANY_ID + "/team/" + MEMBERSHIP_ID))
                .andExpect(status().isNoContent());

        verify(membershipService).revokeMember(COMPANY_ID, MEMBERSHIP_ID, USER_ID);
    }

    @Test
    void revoke_ownerTarget_returns409() throws Exception {
        authenticateAs(USER_ID);
        doThrow(new ConflictException("Cannot revoke the company owner"))
                .when(membershipService).revokeMember(any(), any(), any());

        mockMvc.perform(delete("/companies/" + COMPANY_ID + "/team/" + MEMBERSHIP_ID))
                .andExpect(status().isConflict());
    }

    // ─── GET /companies/{companyId}/me ────────────────────────────────────────

    @Test
    void describeMyAccess_returns200WithRoleAndCapabilities() throws Exception {
        authenticateAs(USER_ID);
        CompanyRoleResponse role = new CompanyRoleResponse(
                CompanyRole.OWNER,
                EnumSet.allOf(CompanyCapability.class));
        when(membershipService.describeMyAccess(COMPANY_ID, USER_ID)).thenReturn(role);

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("OWNER"));
    }

    @Test
    void describeMyAccess_forbidden_returns403() throws Exception {
        authenticateAs(USER_ID);
        when(membershipService.describeMyAccess(any(), any()))
                .thenThrow(new ForbiddenException("You do not have access to this company"));

        mockMvc.perform(get("/companies/" + COMPANY_ID + "/me"))
                .andExpect(status().isForbidden());
    }

    // ─── GET /team-invites/{token} ────────────────────────────────────────────

    @Test
    void previewInvite_returns200() throws Exception {
        TeamInvitePreviewResponse preview = new TeamInvitePreviewResponse(
                "Test Company", "invited@test.com", CompanyRole.MANAGER,
                "Alice", Instant.now().plusSeconds(86400));
        when(membershipService.previewInvite("valid-token")).thenReturn(preview);

        mockMvc.perform(get("/team-invites/valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Test Company"))
                .andExpect(jsonPath("$.invitedEmail").value("invited@test.com"));
    }

    @Test
    void previewInvite_notFound_returns404() throws Exception {
        when(membershipService.previewInvite(any()))
                .thenThrow(new ResourceNotFoundException("Invite not found"));

        mockMvc.perform(get("/team-invites/bad-token"))
                .andExpect(status().isNotFound());
    }

    // ─── POST /team-invites/{token}/accept ───────────────────────────────────

    @Test
    void acceptInvite_returns200() throws Exception {
        authenticateAs(USER_ID);
        TeamMembershipResponse resp = makeMembershipResponse(MEMBERSHIP_ID, USER_ID);
        when(membershipService.acceptInvite("valid-token", USER_ID)).thenReturn(resp);

        mockMvc.perform(post("/team-invites/valid-token/accept"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(MEMBERSHIP_ID.toString()));
    }

    @Test
    void acceptInvite_emailMismatch_returns403() throws Exception {
        authenticateAs(USER_ID);
        when(membershipService.acceptInvite(any(), any()))
                .thenThrow(new ForbiddenException("This invite was sent to a different email address"));

        mockMvc.perform(post("/team-invites/wrong-user-token/accept"))
                .andExpect(status().isForbidden());
    }

    @Test
    void acceptInvite_alreadyMember_returns409() throws Exception {
        authenticateAs(USER_ID);
        when(membershipService.acceptInvite(any(), any()))
                .thenThrow(new ConflictException("You are already an active member of this company"));

        mockMvc.perform(post("/team-invites/dup-token/accept"))
                .andExpect(status().isConflict());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private TeamMembershipResponse makeMembershipResponse(UUID id, UUID userId) {
        return new TeamMembershipResponse(
                id, userId, "Test User", "user@test.com",
                CompanyRole.MANAGER, CompanyMembershipStatus.ACTIVE,
                null, null);
    }

    private static final class NoOpValidator implements Validator {
        @Override public boolean supports(Class<?> clazz) { return true; }
        @Override public void validate(Object target, Errors errors) {}
    }
}
