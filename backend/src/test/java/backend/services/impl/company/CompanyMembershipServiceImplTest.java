package backend.services.impl.company;

import backend.configurations.environment.EnvironmentSetting;
import backend.dtos.requests.company.InviteTeamMemberRequest;
import backend.dtos.requests.company.UpdateTeamMemberRoleRequest;
import backend.dtos.responses.company.CompanyRoleResponse;
import backend.dtos.responses.company.TeamInvitePreviewResponse;
import backend.dtos.responses.company.TeamMembershipResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Company;
import backend.models.core.CompanyMembership;
import backend.models.core.User;
import backend.models.enums.CompanyCapability;
import backend.models.enums.CompanyMembershipStatus;
import backend.models.enums.CompanyRole;
import backend.repositories.CompanyMembershipRepository;
import backend.repositories.UserRepository;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.support.EmailService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CompanyMembershipServiceImplTest {

    private CompanyMembershipRepository membershipRepository;
    private UserRepository userRepository;
    private CompanyAccessService companyAccessService;
    private EmailService emailService;
    private EnvironmentSetting env;
    private EnvironmentSetting.Email emailConf;
    private CompanyMembershipServiceImpl service;

    private static final UUID COMPANY_ID    = TestIds.uuid(1);
    private static final UUID INVITER_ID    = TestIds.uuid(2);
    private static final UUID MEMBER_ID     = TestIds.uuid(3);
    private static final UUID MEMBERSHIP_ID = TestIds.uuid(4);

    @BeforeEach
    void setUp() {
        membershipRepository = mock(CompanyMembershipRepository.class);
        userRepository       = mock(UserRepository.class);
        companyAccessService = mock(CompanyAccessService.class);
        emailService         = mock(EmailService.class);
        env                  = mock(EnvironmentSetting.class);
        emailConf            = mock(EnvironmentSetting.Email.class);

        when(env.getEmail()).thenReturn(emailConf);
        when(emailConf.getVerificationBaseUrl()).thenReturn("http://test.com");

        when(companyAccessService.require(eq(COMPANY_ID), eq(INVITER_ID), any(CompanyCapability.class)))
                .thenReturn(makeCompany(COMPANY_ID));

        service = new CompanyMembershipServiceImpl(
                membershipRepository, userRepository, companyAccessService, emailService, env);
    }

    // ─── inviteMember ────────────────────────────────────────────────────────

    @Test
    void inviteMember_ownerRole_throwsBadRequest() {
        InviteTeamMemberRequest req = makeInviteRequest("dev@test.com", CompanyRole.OWNER);

        assertThrows(BadRequestException.class,
                () -> service.inviteMember(COMPANY_ID, INVITER_ID, req));
        verify(membershipRepository, never()).save(any());
    }

    @Test
    void inviteMember_existingActiveMember_throwsConflict() {
        User existingUser = makeUser(MEMBER_ID, "dev@test.com");
        when(userRepository.findByEmail("dev@test.com")).thenReturn(Optional.of(existingUser));
        when(membershipRepository.existsByCompanyIdAndUserIdAndStatus(
                COMPANY_ID, MEMBER_ID, CompanyMembershipStatus.ACTIVE)).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> service.inviteMember(COMPANY_ID, INVITER_ID, makeInviteRequest("dev@test.com", CompanyRole.EMPLOYEE)));
    }

    @Test
    void inviteMember_pendingInviteAlreadyExists_throwsConflict() {
        when(userRepository.findByEmail("dev@test.com")).thenReturn(Optional.empty());
        when(membershipRepository.findByCompanyIdAndInviteEmailIgnoreCaseAndStatus(
                COMPANY_ID, "dev@test.com", CompanyMembershipStatus.PENDING))
                .thenReturn(Optional.of(new CompanyMembership()));

        assertThrows(ConflictException.class,
                () -> service.inviteMember(COMPANY_ID, INVITER_ID, makeInviteRequest("dev@test.com", CompanyRole.EMPLOYEE)));
    }

    @Test
    void inviteMember_createsPendingMembershipWithToken() {
        when(userRepository.findByEmail("dev@test.com")).thenReturn(Optional.empty());
        when(membershipRepository.findByCompanyIdAndInviteEmailIgnoreCaseAndStatus(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(userRepository.getReferenceById(INVITER_ID)).thenReturn(makeUser(INVITER_ID, "inviter@test.com"));
        when(membershipRepository.save(any())).thenAnswer(inv -> {
            CompanyMembership m = inv.getArgument(0);
            m.setId(MEMBERSHIP_ID);
            return m;
        });

        service.inviteMember(COMPANY_ID, INVITER_ID, makeInviteRequest("dev@test.com", CompanyRole.EMPLOYEE));

        ArgumentCaptor<CompanyMembership> cap = ArgumentCaptor.forClass(CompanyMembership.class);
        verify(membershipRepository).save(cap.capture());
        CompanyMembership saved = cap.getValue();
        assertEquals(CompanyMembershipStatus.PENDING, saved.getStatus());
        assertEquals(CompanyRole.EMPLOYEE, saved.getRole());
        assertEquals("dev@test.com", saved.getInviteEmail());
        assertNotNull(saved.getInviteToken());
        assertNotNull(saved.getInviteExpiresAt());
    }

    @Test
    void inviteMember_setsInviteExpiry7DaysFromNow() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(membershipRepository.findByCompanyIdAndInviteEmailIgnoreCaseAndStatus(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(userRepository.getReferenceById(INVITER_ID)).thenReturn(makeUser(INVITER_ID, "inviter@test.com"));
        when(membershipRepository.save(any())).thenAnswer(inv -> {
            CompanyMembership m = inv.getArgument(0);
            m.setId(MEMBERSHIP_ID);
            return m;
        });

        Instant before = Instant.now().plusSeconds(6 * 24 * 3600);

        service.inviteMember(COMPANY_ID, INVITER_ID, makeInviteRequest("dev@test.com", CompanyRole.MANAGER));

        ArgumentCaptor<CompanyMembership> cap = ArgumentCaptor.forClass(CompanyMembership.class);
        verify(membershipRepository).save(cap.capture());
        assertTrue(cap.getValue().getInviteExpiresAt().isAfter(before));
    }

    @Test
    void inviteMember_sendsInviteEmail() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(membershipRepository.findByCompanyIdAndInviteEmailIgnoreCaseAndStatus(any(), any(), any()))
                .thenReturn(Optional.empty());
        User inviter = makeUser(INVITER_ID, "inviter@test.com");
        inviter.setFirstName("Alice");
        when(userRepository.getReferenceById(INVITER_ID)).thenReturn(inviter);
        when(membershipRepository.save(any())).thenAnswer(inv -> {
            CompanyMembership m = inv.getArgument(0);
            m.setId(MEMBERSHIP_ID);
            return m;
        });

        service.inviteMember(COMPANY_ID, INVITER_ID, makeInviteRequest("dev@test.com", CompanyRole.MANAGER));

        verify(emailService).sendTeamInviteEmail(
                eq("dev@test.com"), eq("Test Company"),
                eq("Manager"), eq("Alice"), anyString());
    }

    @Test
    void inviteMember_emailSendFailure_doesNotPreventMembershipCreation() {
        when(userRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(membershipRepository.findByCompanyIdAndInviteEmailIgnoreCaseAndStatus(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(userRepository.getReferenceById(INVITER_ID)).thenReturn(makeUser(INVITER_ID, "inviter@test.com"));
        when(membershipRepository.save(any())).thenAnswer(inv -> {
            CompanyMembership m = inv.getArgument(0);
            m.setId(MEMBERSHIP_ID);
            return m;
        });
        doThrow(new RuntimeException("SMTP error")).when(emailService)
                .sendTeamInviteEmail(any(), any(), any(), any(), any());

        assertDoesNotThrow(() ->
                service.inviteMember(COMPANY_ID, INVITER_ID, makeInviteRequest("dev@test.com", CompanyRole.EMPLOYEE)));
        verify(membershipRepository).save(any());
    }

    // ─── listMembers ─────────────────────────────────────────────────────────

    @Test
    void listMembers_returnsActiveAndPendingMembers() {
        CompanyMembership active = makeMembership(MEMBERSHIP_ID, CompanyRole.MANAGER, CompanyMembershipStatus.ACTIVE);
        CompanyMembership pending = makeMembership(TestIds.uuid(5), CompanyRole.EMPLOYEE, CompanyMembershipStatus.PENDING);
        when(membershipRepository.findAllByCompanyIdAndStatusIn(
                eq(COMPANY_ID), eq(List.of(CompanyMembershipStatus.ACTIVE, CompanyMembershipStatus.PENDING))))
                .thenReturn(List.of(active, pending));

        List<TeamMembershipResponse> result = service.listMembers(COMPANY_ID, INVITER_ID);

        assertEquals(2, result.size());
    }

    // ─── updateMemberRole ────────────────────────────────────────────────────

    @Test
    void updateMemberRole_memberNotFound_throwsResourceNotFound() {
        when(membershipRepository.findById(MEMBERSHIP_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.updateMemberRole(COMPANY_ID, MEMBERSHIP_ID, INVITER_ID,
                        makeUpdateRoleRequest(CompanyRole.MANAGER)));
    }

    @Test
    void updateMemberRole_targetIsOwner_throwsConflict() {
        CompanyMembership m = makeMembership(MEMBERSHIP_ID, CompanyRole.OWNER, CompanyMembershipStatus.ACTIVE);
        m.setCompany(makeCompanyWithId(COMPANY_ID));
        when(membershipRepository.findById(MEMBERSHIP_ID)).thenReturn(Optional.of(m));

        assertThrows(ConflictException.class,
                () -> service.updateMemberRole(COMPANY_ID, MEMBERSHIP_ID, INVITER_ID,
                        makeUpdateRoleRequest(CompanyRole.MANAGER)));
    }

    @Test
    void updateMemberRole_newRoleIsOwner_throwsBadRequest() {
        CompanyMembership m = makeMembership(MEMBERSHIP_ID, CompanyRole.EMPLOYEE, CompanyMembershipStatus.ACTIVE);
        m.setCompany(makeCompanyWithId(COMPANY_ID));
        when(membershipRepository.findById(MEMBERSHIP_ID)).thenReturn(Optional.of(m));

        assertThrows(BadRequestException.class,
                () -> service.updateMemberRole(COMPANY_ID, MEMBERSHIP_ID, INVITER_ID,
                        makeUpdateRoleRequest(CompanyRole.OWNER)));
    }

    @Test
    void updateMemberRole_updatesRoleAndInvalidatesCacheForActiveUser() {
        User member = makeUser(MEMBER_ID, "member@test.com");
        CompanyMembership m = makeMembership(MEMBERSHIP_ID, CompanyRole.EMPLOYEE, CompanyMembershipStatus.ACTIVE);
        m.setCompany(makeCompanyWithId(COMPANY_ID));
        m.setUser(member);
        when(membershipRepository.findById(MEMBERSHIP_ID)).thenReturn(Optional.of(m));
        when(membershipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateMemberRole(COMPANY_ID, MEMBERSHIP_ID, INVITER_ID, makeUpdateRoleRequest(CompanyRole.MANAGER));

        ArgumentCaptor<CompanyMembership> cap = ArgumentCaptor.forClass(CompanyMembership.class);
        verify(membershipRepository).save(cap.capture());
        assertEquals(CompanyRole.MANAGER, cap.getValue().getRole());
        verify(companyAccessService).invalidate(COMPANY_ID, MEMBER_ID);
    }

    @Test
    void updateMemberRole_pendingMemberNoUser_doesNotInvalidate() {
        CompanyMembership m = makeMembership(MEMBERSHIP_ID, CompanyRole.EMPLOYEE, CompanyMembershipStatus.PENDING);
        m.setCompany(makeCompanyWithId(COMPANY_ID));
        m.setUser(null);
        when(membershipRepository.findById(MEMBERSHIP_ID)).thenReturn(Optional.of(m));
        when(membershipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateMemberRole(COMPANY_ID, MEMBERSHIP_ID, INVITER_ID, makeUpdateRoleRequest(CompanyRole.MANAGER));

        // Only the access service call from companyAccessService.require in setUp was already stubbed
        verify(companyAccessService, never()).invalidate(any(), any());
    }

    // ─── revokeMember ────────────────────────────────────────────────────────

    @Test
    void revokeMember_memberNotFound_throwsResourceNotFound() {
        when(membershipRepository.findById(MEMBERSHIP_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.revokeMember(COMPANY_ID, MEMBERSHIP_ID, INVITER_ID));
    }

    @Test
    void revokeMember_targetIsOwner_throwsConflict() {
        CompanyMembership m = makeMembership(MEMBERSHIP_ID, CompanyRole.OWNER, CompanyMembershipStatus.ACTIVE);
        m.setCompany(makeCompanyWithId(COMPANY_ID));
        when(membershipRepository.findById(MEMBERSHIP_ID)).thenReturn(Optional.of(m));

        assertThrows(ConflictException.class,
                () -> service.revokeMember(COMPANY_ID, MEMBERSHIP_ID, INVITER_ID));
    }

    @Test
    void revokeMember_setsStatusRevokedAndClearsToken() {
        CompanyMembership m = makeMembership(MEMBERSHIP_ID, CompanyRole.MANAGER, CompanyMembershipStatus.ACTIVE);
        m.setCompany(makeCompanyWithId(COMPANY_ID));
        m.setInviteToken("some-token");
        when(membershipRepository.findById(MEMBERSHIP_ID)).thenReturn(Optional.of(m));
        when(membershipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.revokeMember(COMPANY_ID, MEMBERSHIP_ID, INVITER_ID);

        ArgumentCaptor<CompanyMembership> cap = ArgumentCaptor.forClass(CompanyMembership.class);
        verify(membershipRepository).save(cap.capture());
        assertEquals(CompanyMembershipStatus.REVOKED, cap.getValue().getStatus());
        assertNull(cap.getValue().getInviteToken());
    }

    @Test
    void revokeMember_activeUser_invalidatesAccessCache() {
        User member = makeUser(MEMBER_ID, "member@test.com");
        CompanyMembership m = makeMembership(MEMBERSHIP_ID, CompanyRole.MANAGER, CompanyMembershipStatus.ACTIVE);
        m.setCompany(makeCompanyWithId(COMPANY_ID));
        m.setUser(member);
        when(membershipRepository.findById(MEMBERSHIP_ID)).thenReturn(Optional.of(m));
        when(membershipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.revokeMember(COMPANY_ID, MEMBERSHIP_ID, INVITER_ID);

        verify(companyAccessService).invalidate(COMPANY_ID, MEMBER_ID);
    }

    // ─── previewInvite ────────────────────────────────────────────────────────

    @Test
    void previewInvite_blankToken_throwsResourceNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.previewInvite("   "));
    }

    @Test
    void previewInvite_tokenNotFound_throwsResourceNotFound() {
        when(membershipRepository.findByInviteToken("bad-token")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.previewInvite("bad-token"));
    }

    @Test
    void previewInvite_expiredInvite_throwsResourceNotFound() {
        CompanyMembership m = makeMembership(MEMBERSHIP_ID, CompanyRole.MANAGER, CompanyMembershipStatus.PENDING);
        m.setCompany(makeCompany(COMPANY_ID));
        m.setInviteExpiresAt(Instant.now().minusSeconds(1));
        when(membershipRepository.findByInviteToken("expired")).thenReturn(Optional.of(m));

        assertThrows(ResourceNotFoundException.class, () -> service.previewInvite("expired"));
    }

    @Test
    void previewInvite_notPendingStatus_throwsResourceNotFound() {
        CompanyMembership m = makeMembership(MEMBERSHIP_ID, CompanyRole.MANAGER, CompanyMembershipStatus.ACTIVE);
        m.setCompany(makeCompany(COMPANY_ID));
        m.setInviteExpiresAt(Instant.now().plusSeconds(3600));
        when(membershipRepository.findByInviteToken("tok")).thenReturn(Optional.of(m));

        assertThrows(ResourceNotFoundException.class, () -> service.previewInvite("tok"));
    }

    @Test
    void previewInvite_validToken_returnsPreview() {
        CompanyMembership m = makeMembership(MEMBERSHIP_ID, CompanyRole.MANAGER, CompanyMembershipStatus.PENDING);
        m.setCompany(makeCompany(COMPANY_ID));
        m.setInviteEmail("invited@test.com");
        m.setInviteExpiresAt(Instant.now().plusSeconds(3600));
        when(membershipRepository.findByInviteToken("valid")).thenReturn(Optional.of(m));

        TeamInvitePreviewResponse result = service.previewInvite("valid");

        assertEquals("Test Company", result.companyName());
        assertEquals("invited@test.com", result.invitedEmail());
        assertEquals(CompanyRole.MANAGER, result.role());
    }

    // ─── acceptInvite ────────────────────────────────────────────────────────

    @Test
    void acceptInvite_emailMismatch_throwsForbidden() {
        CompanyMembership invite = makePendingInvite("invited@test.com");
        when(membershipRepository.findByInviteToken("tok")).thenReturn(Optional.of(invite));
        User acceptor = makeUser(MEMBER_ID, "different@test.com");
        when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(acceptor));

        assertThrows(ForbiddenException.class, () -> service.acceptInvite("tok", MEMBER_ID));
    }

    @Test
    void acceptInvite_alreadyActiveMember_throwsConflict() {
        CompanyMembership invite = makePendingInvite("invited@test.com");
        when(membershipRepository.findByInviteToken("tok")).thenReturn(Optional.of(invite));
        User acceptor = makeUser(MEMBER_ID, "invited@test.com");
        when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(acceptor));
        when(membershipRepository.existsByCompanyIdAndUserIdAndStatus(
                COMPANY_ID, MEMBER_ID, CompanyMembershipStatus.ACTIVE)).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.acceptInvite("tok", MEMBER_ID));
    }

    @Test
    void acceptInvite_validAcceptance_setsActiveAndClearsToken() {
        CompanyMembership invite = makePendingInvite("invited@test.com");
        when(membershipRepository.findByInviteToken("tok")).thenReturn(Optional.of(invite));
        User acceptor = makeUser(MEMBER_ID, "invited@test.com");
        when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(acceptor));
        when(membershipRepository.existsByCompanyIdAndUserIdAndStatus(any(), any(), any())).thenReturn(false);
        when(membershipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.acceptInvite("tok", MEMBER_ID);

        ArgumentCaptor<CompanyMembership> cap = ArgumentCaptor.forClass(CompanyMembership.class);
        verify(membershipRepository).save(cap.capture());
        assertEquals(CompanyMembershipStatus.ACTIVE, cap.getValue().getStatus());
        assertNull(cap.getValue().getInviteToken());
        assertNotNull(cap.getValue().getAcceptedAt());
        assertSame(acceptor, cap.getValue().getUser());
    }

    @Test
    void acceptInvite_invalidatesAccessCache() {
        CompanyMembership invite = makePendingInvite("invited@test.com");
        when(membershipRepository.findByInviteToken("tok")).thenReturn(Optional.of(invite));
        User acceptor = makeUser(MEMBER_ID, "invited@test.com");
        when(userRepository.findById(MEMBER_ID)).thenReturn(Optional.of(acceptor));
        when(membershipRepository.existsByCompanyIdAndUserIdAndStatus(any(), any(), any())).thenReturn(false);
        when(membershipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.acceptInvite("tok", MEMBER_ID);

        verify(companyAccessService).invalidate(COMPANY_ID, MEMBER_ID);
    }

    // ─── describeMyAccess ────────────────────────────────────────────────────

    @Test
    void describeMyAccess_returnsRoleAndCapabilities() {
        when(companyAccessService.resolveRole(COMPANY_ID, MEMBER_ID))
                .thenReturn(Optional.of(CompanyRole.OWNER));

        CompanyRoleResponse result = service.describeMyAccess(COMPANY_ID, MEMBER_ID);

        assertEquals(CompanyRole.OWNER, result.role());
        assertTrue(result.capabilities().contains(CompanyCapability.MANAGE_COMPANY));
        assertTrue(result.capabilities().contains(CompanyCapability.MANAGE_PRODUCTS));
    }

    @Test
    void describeMyAccess_noAccess_throwsForbidden() {
        when(companyAccessService.resolveRole(COMPANY_ID, MEMBER_ID)).thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class,
                () -> service.describeMyAccess(COMPANY_ID, MEMBER_ID));
    }

    @Test
    void describeMyAccess_employeeRole_hasLimitedCapabilities() {
        when(companyAccessService.resolveRole(COMPANY_ID, MEMBER_ID))
                .thenReturn(Optional.of(CompanyRole.EMPLOYEE));

        CompanyRoleResponse result = service.describeMyAccess(COMPANY_ID, MEMBER_ID);

        assertFalse(result.capabilities().contains(CompanyCapability.MANAGE_COMPANY));
        assertTrue(result.capabilities().contains(CompanyCapability.FULFILL_ORDERS));
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private User makeUser(UUID id, String email) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        return u;
    }

    private Company makeCompany(UUID id) {
        Company c = new Company();
        c.setId(id);
        c.setName("Test Company");
        return c;
    }

    private Company makeCompanyWithId(UUID id) {
        return makeCompany(id);
    }

    private CompanyMembership makeMembership(UUID id, CompanyRole role, CompanyMembershipStatus status) {
        CompanyMembership m = new CompanyMembership();
        m.setId(id);
        m.setRole(role);
        m.setStatus(status);
        return m;
    }

    private CompanyMembership makePendingInvite(String email) {
        Company company = makeCompany(COMPANY_ID);
        CompanyMembership m = new CompanyMembership();
        m.setId(MEMBERSHIP_ID);
        m.setCompany(company);
        m.setInviteEmail(email);
        m.setRole(CompanyRole.MANAGER);
        m.setStatus(CompanyMembershipStatus.PENDING);
        m.setInviteToken("tok");
        m.setInviteExpiresAt(Instant.now().plusSeconds(86400));
        return m;
    }

    private InviteTeamMemberRequest makeInviteRequest(String email, CompanyRole role) {
        InviteTeamMemberRequest req = new InviteTeamMemberRequest();
        req.setEmail(email);
        req.setRole(role);
        return req;
    }

    private UpdateTeamMemberRoleRequest makeUpdateRoleRequest(CompanyRole role) {
        UpdateTeamMemberRoleRequest req = new UpdateTeamMemberRoleRequest();
        req.setRole(role);
        return req;
    }
}
