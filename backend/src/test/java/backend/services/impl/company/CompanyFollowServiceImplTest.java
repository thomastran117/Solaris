package backend.services.impl.company;

import backend.dtos.responses.company.CompanyFollowResponse;
import backend.dtos.responses.company.FollowStatusResponse;
import backend.dtos.responses.company.FollowedCompanyResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Company;
import backend.models.core.CompanyFollow;
import backend.models.core.User;
import backend.models.enums.CompanyStatus;
import backend.repositories.CompanyFollowRepository;
import backend.repositories.CompanyRepository;
import backend.repositories.UserRepository;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CompanyFollowServiceImplTest {

    private CompanyFollowRepository followRepository;
    private CompanyRepository companyRepository;
    private UserRepository userRepository;
    private CompanyFollowServiceImpl service;

    private static final UUID USER_ID    = TestIds.uuid(1);
    private static final UUID COMPANY_ID = TestIds.uuid(2);
    private static final UUID FOLLOW_ID  = TestIds.uuid(3);

    @BeforeEach
    void setUp() {
        followRepository  = mock(CompanyFollowRepository.class);
        companyRepository = mock(CompanyRepository.class);
        userRepository    = mock(UserRepository.class);
        service = new CompanyFollowServiceImpl(followRepository, companyRepository, userRepository);
    }

    // ─── follow ──────────────────────────────────────────────────────────────

    @Test
    void follow_happyPath_savesFollowAndReturnsResponse() {
        Company company = makeCompany(COMPANY_ID, CompanyStatus.ACTIVE);
        User user = makeUser(USER_ID);
        CompanyFollow saved = makeFollow(FOLLOW_ID, user, company);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(followRepository.existsByUserIdAndCompanyId(USER_ID, COMPANY_ID)).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(followRepository.save(any())).thenReturn(saved);

        CompanyFollowResponse response = service.follow(USER_ID, COMPANY_ID);

        assertEquals(FOLLOW_ID, response.getId());
        assertEquals(COMPANY_ID, response.getCompanyId());
        assertTrue(response.isNotificationsEnabled());
    }

    @Test
    void follow_companyNotFound_throwsResourceNotFound() {
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.follow(USER_ID, COMPANY_ID));
        verify(followRepository, never()).save(any());
    }

    @Test
    void follow_inactiveCompany_throwsBadRequest() {
        when(companyRepository.findById(COMPANY_ID))
                .thenReturn(Optional.of(makeCompany(COMPANY_ID, CompanyStatus.INACTIVE)));

        assertThrows(BadRequestException.class,
                () -> service.follow(USER_ID, COMPANY_ID));
        verify(followRepository, never()).save(any());
    }

    @Test
    void follow_alreadyFollowing_throwsConflict() {
        when(companyRepository.findById(COMPANY_ID))
                .thenReturn(Optional.of(makeCompany(COMPANY_ID, CompanyStatus.ACTIVE)));
        when(followRepository.existsByUserIdAndCompanyId(USER_ID, COMPANY_ID)).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> service.follow(USER_ID, COMPANY_ID));
        verify(followRepository, never()).save(any());
    }

    @Test
    void follow_setsFollowedAtAndNotificationsEnabled() {
        Company company = makeCompany(COMPANY_ID, CompanyStatus.ACTIVE);
        User user = makeUser(USER_ID);
        CompanyFollow saved = makeFollow(FOLLOW_ID, user, company);

        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(followRepository.existsByUserIdAndCompanyId(USER_ID, COMPANY_ID)).thenReturn(false);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(followRepository.save(any())).thenReturn(saved);

        service.follow(USER_ID, COMPANY_ID);

        ArgumentCaptor<CompanyFollow> captor = ArgumentCaptor.forClass(CompanyFollow.class);
        verify(followRepository).save(captor.capture());
        assertNotNull(captor.getValue().getFollowedAt());
        assertTrue(captor.getValue().isNotificationsEnabled());
    }

    // ─── unfollow ────────────────────────────────────────────────────────────

    @Test
    void unfollow_existingFollow_deletesIt() {
        CompanyFollow follow = makeFollow(FOLLOW_ID, makeUser(USER_ID), makeCompany(COMPANY_ID, CompanyStatus.ACTIVE));
        when(followRepository.findByUserIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(Optional.of(follow));

        service.unfollow(USER_ID, COMPANY_ID);

        verify(followRepository).delete(follow);
    }

    @Test
    void unfollow_notFollowing_isIdempotentAndDoesNotThrow() {
        when(followRepository.findByUserIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.unfollow(USER_ID, COMPANY_ID));
        verify(followRepository, never()).delete(any());
    }

    // ─── getStatus ───────────────────────────────────────────────────────────

    @Test
    void getStatus_authenticated_followingWithNotificationsOn_returnsFullStatus() {
        CompanyFollow follow = makeFollow(FOLLOW_ID, makeUser(USER_ID), makeCompany(COMPANY_ID, CompanyStatus.ACTIVE));
        follow.setNotificationsEnabled(true);
        when(followRepository.countByCompanyId(COMPANY_ID)).thenReturn(5L);
        when(followRepository.findByUserIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(Optional.of(follow));

        FollowStatusResponse status = service.getStatus(USER_ID, COMPANY_ID);

        assertTrue(status.isFollowing());
        assertTrue(status.isNotificationsEnabled());
        assertEquals(5L, status.getFollowerCount());
    }

    @Test
    void getStatus_authenticated_notFollowing_returnsFollowingFalse() {
        when(followRepository.countByCompanyId(COMPANY_ID)).thenReturn(3L);
        when(followRepository.findByUserIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(Optional.empty());

        FollowStatusResponse status = service.getStatus(USER_ID, COMPANY_ID);

        assertFalse(status.isFollowing());
        assertFalse(status.isNotificationsEnabled());
        assertEquals(3L, status.getFollowerCount());
    }

    @Test
    void getStatus_anonymous_returnsOnlyFollowerCount() {
        when(followRepository.countByCompanyId(COMPANY_ID)).thenReturn(12L);

        FollowStatusResponse status = service.getStatus(null, COMPANY_ID);

        assertFalse(status.isFollowing());
        assertEquals(12L, status.getFollowerCount());
        verify(followRepository, never()).findByUserIdAndCompanyId(any(), any());
    }

    // ─── setNotifications ────────────────────────────────────────────────────

    @Test
    void setNotifications_flipsEnabledAndSaves() {
        CompanyFollow follow = makeFollow(FOLLOW_ID, makeUser(USER_ID), makeCompany(COMPANY_ID, CompanyStatus.ACTIVE));
        follow.setNotificationsEnabled(true);
        when(followRepository.findByUserIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(Optional.of(follow));
        when(followRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.setNotifications(USER_ID, COMPANY_ID, false);

        ArgumentCaptor<CompanyFollow> captor = ArgumentCaptor.forClass(CompanyFollow.class);
        verify(followRepository).save(captor.capture());
        assertFalse(captor.getValue().isNotificationsEnabled());
    }

    @Test
    void setNotifications_notFollowing_throwsResourceNotFound() {
        when(followRepository.findByUserIdAndCompanyId(USER_ID, COMPANY_ID))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.setNotifications(USER_ID, COMPANY_ID, false));
        verify(followRepository, never()).save(any());
    }

    // ─── listFollowing ───────────────────────────────────────────────────────

    @Test
    void listFollowing_delegatesWithJoinFetchAndMapsToResponse() {
        Company company = makeCompany(COMPANY_ID, CompanyStatus.ACTIVE);
        company.setName("Acme");
        User user = makeUser(USER_ID);
        CompanyFollow follow = makeFollow(FOLLOW_ID, user, company);

        when(followRepository.findAllByUserIdWithCompany(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(follow)));

        PagedResponse<FollowedCompanyResponse> result = service.listFollowing(USER_ID, 0, 20);

        assertEquals(1, result.getItems().size());
        FollowedCompanyResponse item = result.getItems().get(0);
        assertEquals(FOLLOW_ID, item.getFollowId());
        assertEquals(COMPANY_ID, item.getCompanyId());
        assertEquals("Acme", item.getCompanyName());
    }

    @Test
    void listFollowing_emptyPage_returnsEmptyList() {
        when(followRepository.findAllByUserIdWithCompany(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        PagedResponse<FollowedCompanyResponse> result = service.listFollowing(USER_ID, 0, 20);

        assertTrue(result.getItems().isEmpty());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private User makeUser(UUID id) {
        User u = new User();
        u.setId(id);
        u.setEmail("user-" + id + "@test.com");
        u.setFirstName("Test");
        return u;
    }

    private Company makeCompany(UUID id, CompanyStatus status) {
        Company c = new Company();
        c.setId(id);
        c.setName("Test Company");
        c.setStatus(status);
        return c;
    }

    private CompanyFollow makeFollow(UUID id, User user, Company company) {
        CompanyFollow f = new CompanyFollow();
        f.setId(id);
        f.setUser(user);
        f.setCompany(company);
        f.setFollowedAt(Instant.now());
        f.setNotificationsEnabled(true);
        return f;
    }
}
