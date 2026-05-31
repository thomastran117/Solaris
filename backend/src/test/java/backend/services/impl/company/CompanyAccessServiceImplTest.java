package backend.services.impl.company;

import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Company;
import backend.models.core.CompanyMembership;
import backend.models.enums.CompanyCapability;
import backend.models.enums.CompanyMembershipStatus;
import backend.models.enums.CompanyRole;
import backend.repositories.CompanyMembershipRepository;
import backend.repositories.CompanyRepository;
import backend.services.intf.CacheService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CompanyAccessServiceImplTest {

    private CompanyRepository companyRepository;
    private CompanyMembershipRepository membershipRepository;
    private CacheService cacheService;
    private CompanyAccessServiceImpl service;

    private static final UUID COMPANY_ID = TestIds.uuid(1);
    private static final UUID USER_ID    = TestIds.uuid(2);
    private static final String CACHE_KEY = "company:access:" + COMPANY_ID + ":" + USER_ID;

    @BeforeEach
    void setUp() {
        companyRepository   = mock(CompanyRepository.class);
        membershipRepository = mock(CompanyMembershipRepository.class);
        cacheService        = mock(CacheService.class);
        service = new CompanyAccessServiceImpl(companyRepository, membershipRepository, cacheService);
    }

    // ─── resolveRole ─────────────────────────────────────────────────────────

    @Test
    void resolveRole_cacheHit_returnsRoleWithoutDbCall() {
        when(cacheService.get(CACHE_KEY)).thenReturn("MANAGER");

        Optional<CompanyRole> role = service.resolveRole(COMPANY_ID, USER_ID);

        assertEquals(Optional.of(CompanyRole.MANAGER), role);
        verifyNoInteractions(membershipRepository);
    }

    @Test
    void resolveRole_cacheHitEmpty_returnsEmptyWithoutDbCall() {
        when(cacheService.get(CACHE_KEY)).thenReturn("");

        Optional<CompanyRole> role = service.resolveRole(COMPANY_ID, USER_ID);

        assertTrue(role.isEmpty());
        verifyNoInteractions(membershipRepository);
    }

    @Test
    void resolveRole_cacheMiss_queriesDbAndCachesResult() {
        when(cacheService.get(CACHE_KEY)).thenReturn(null);
        CompanyMembership m = makeMembership(CompanyRole.EMPLOYEE);
        when(membershipRepository.findByCompanyIdAndUserIdAndStatus(
                COMPANY_ID, USER_ID, CompanyMembershipStatus.ACTIVE))
                .thenReturn(Optional.of(m));

        Optional<CompanyRole> role = service.resolveRole(COMPANY_ID, USER_ID);

        assertEquals(Optional.of(CompanyRole.EMPLOYEE), role);
        verify(cacheService).set(eq(CACHE_KEY), eq("EMPLOYEE"), eq(60L));
    }

    @Test
    void resolveRole_cacheReadError_fallsThroughToDb() {
        when(cacheService.get(CACHE_KEY)).thenThrow(new RuntimeException("Redis down"));
        CompanyMembership m = makeMembership(CompanyRole.OWNER);
        when(membershipRepository.findByCompanyIdAndUserIdAndStatus(any(), any(), any()))
                .thenReturn(Optional.of(m));

        Optional<CompanyRole> role = service.resolveRole(COMPANY_ID, USER_ID);

        assertEquals(Optional.of(CompanyRole.OWNER), role);
    }

    @Test
    void resolveRole_noMembership_cachesEmptyAndReturnsEmpty() {
        when(cacheService.get(CACHE_KEY)).thenReturn(null);
        when(membershipRepository.findByCompanyIdAndUserIdAndStatus(any(), any(), any()))
                .thenReturn(Optional.empty());

        Optional<CompanyRole> role = service.resolveRole(COMPANY_ID, USER_ID);

        assertTrue(role.isEmpty());
        verify(cacheService).set(eq(CACHE_KEY), eq(""), anyLong());
    }

    @Test
    void resolveRole_cacheWriteError_stillReturnsRole() {
        when(cacheService.get(CACHE_KEY)).thenReturn(null);
        CompanyMembership m = makeMembership(CompanyRole.MANAGER);
        when(membershipRepository.findByCompanyIdAndUserIdAndStatus(any(), any(), any()))
                .thenReturn(Optional.of(m));
        doThrow(new RuntimeException("Redis write failed")).when(cacheService).set(any(), any(), anyLong());

        Optional<CompanyRole> role = service.resolveRole(COMPANY_ID, USER_ID);

        assertEquals(Optional.of(CompanyRole.MANAGER), role);
    }

    // ─── require ─────────────────────────────────────────────────────────────

    @Test
    void require_noRole_throwsForbidden() {
        when(cacheService.get(any())).thenReturn("");

        assertThrows(ForbiddenException.class,
                () -> service.require(COMPANY_ID, USER_ID, CompanyCapability.MANAGE_COMPANY));
    }

    @Test
    void require_roleWithoutCapability_throwsForbidden() {
        // EMPLOYEE does not have MANAGE_COMPANY
        when(cacheService.get(any())).thenReturn("EMPLOYEE");

        assertThrows(ForbiddenException.class,
                () -> service.require(COMPANY_ID, USER_ID, CompanyCapability.MANAGE_COMPANY));
    }

    @Test
    void require_roleWithCapability_loadsAndReturnsCompany() {
        when(cacheService.get(any())).thenReturn("OWNER");
        Company company = makeCompany(COMPANY_ID);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));

        Company result = service.require(COMPANY_ID, USER_ID, CompanyCapability.MANAGE_COMPANY);

        assertSame(company, result);
    }

    @Test
    void require_companyNotFoundAfterRoleCheck_throwsResourceNotFound() {
        when(cacheService.get(any())).thenReturn("OWNER");
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.require(COMPANY_ID, USER_ID, CompanyCapability.MANAGE_COMPANY));
    }

    // ─── requireAnyAccess ────────────────────────────────────────────────────

    @Test
    void requireAnyAccess_noRole_throwsForbidden() {
        when(cacheService.get(any())).thenReturn("");

        assertThrows(ForbiddenException.class,
                () -> service.requireAnyAccess(COMPANY_ID, USER_ID));
    }

    @Test
    void requireAnyAccess_hasAnyRole_returnsCompany() {
        when(cacheService.get(any())).thenReturn("EMPLOYEE");
        Company company = makeCompany(COMPANY_ID);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));

        Company result = service.requireAnyAccess(COMPANY_ID, USER_ID);

        assertSame(company, result);
    }

    // ─── listAccessibleCompanies ──────────────────────────────────────────────

    @Test
    void listAccessibleCompanies_delegatesToRepository() {
        Company c = makeCompany(COMPANY_ID);
        when(membershipRepository.findCompaniesAccessibleByUser(USER_ID, CompanyMembershipStatus.ACTIVE))
                .thenReturn(List.of(c));

        List<Company> result = service.listAccessibleCompanies(USER_ID);

        assertEquals(1, result.size());
        assertSame(c, result.get(0));
    }

    // ─── roleHas ─────────────────────────────────────────────────────────────

    @Test
    void roleHas_ownerHasManageCompany() {
        assertTrue(service.roleHas(CompanyRole.OWNER, CompanyCapability.MANAGE_COMPANY));
    }

    @Test
    void roleHas_employeeDoesNotHaveManageCompany() {
        assertFalse(service.roleHas(CompanyRole.EMPLOYEE, CompanyCapability.MANAGE_COMPANY));
    }

    @Test
    void roleHas_managerHasManageProducts() {
        assertTrue(service.roleHas(CompanyRole.MANAGER, CompanyCapability.MANAGE_PRODUCTS));
    }

    // ─── invalidate ──────────────────────────────────────────────────────────

    @Test
    void invalidate_deletesCorrectCacheKey() {
        service.invalidate(COMPANY_ID, USER_ID);
        verify(cacheService).delete(CACHE_KEY);
    }

    @Test
    void invalidate_cacheError_doesNotPropagate() {
        doThrow(new RuntimeException("Redis down")).when(cacheService).delete(any());
        assertDoesNotThrow(() -> service.invalidate(COMPANY_ID, USER_ID));
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private Company makeCompany(UUID id) {
        Company c = new Company();
        c.setId(id);
        return c;
    }

    private CompanyMembership makeMembership(CompanyRole role) {
        CompanyMembership m = new CompanyMembership();
        m.setRole(role);
        return m;
    }
}
