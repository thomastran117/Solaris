package backend.services.impl.company;

import backend.dtos.requests.company.CreateCompanyRequest;
import backend.dtos.requests.company.UpdateCompanyRequest;
import backend.dtos.responses.company.CompanyResponse;
import backend.dtos.responses.company.PublicCompanyResponse;
import backend.dtos.responses.upload.PresignUploadResponse;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Company;
import backend.models.core.CompanyMembership;
import backend.models.core.User;
import backend.models.enums.CompanyCapability;
import backend.models.enums.CompanyMembershipStatus;
import backend.models.enums.CompanyRole;
import backend.models.enums.CompanyStatus;
import backend.models.enums.UploadFolder;
import backend.repositories.CompanyMembershipRepository;
import backend.repositories.CompanyRepository;
import backend.repositories.UserRepository;
import backend.services.intf.StorageService;
import backend.services.intf.company.CompanyAccessService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CompanyServiceImplTest {

    private CompanyRepository companyRepository;
    private CompanyMembershipRepository membershipRepository;
    private UserRepository userRepository;
    private StorageService storageService;
    private CompanyAccessService companyAccessService;
    private CompanyServiceImpl service;

    private static final UUID OWNER_ID   = TestIds.uuid(1);
    private static final UUID COMPANY_ID = TestIds.uuid(2);

    @BeforeEach
    void setUp() {
        companyRepository   = mock(CompanyRepository.class);
        membershipRepository = mock(CompanyMembershipRepository.class);
        userRepository      = mock(UserRepository.class);
        storageService      = mock(StorageService.class);
        companyAccessService = mock(CompanyAccessService.class);
        service = new CompanyServiceImpl(companyRepository, membershipRepository,
                userRepository, storageService, companyAccessService);
    }

    // ─── searchCompanies ─────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void searchCompanies_clampsPageSizeTo50() {
        when(companyRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.searchCompanies(null, null, null, null, 0, 200, null, null);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(companyRepository).findAll(any(Specification.class), captor.capture());
        assertEquals(50, captor.getValue().getPageSize());
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchCompanies_invalidSortField_defaultsToCreatedAt() {
        when(companyRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.searchCompanies(null, null, null, null, 0, 20, "invalidField", "desc");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(companyRepository).findAll(any(Specification.class), captor.capture());
        assertEquals("createdAt", captor.getValue().getSort().iterator().next().getProperty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchCompanies_validSortField_usesIt() {
        when(companyRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.searchCompanies(null, null, null, null, 0, 20, "name", "asc");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(companyRepository).findAll(any(Specification.class), captor.capture());
        var sortOrder = captor.getValue().getSort().iterator().next();
        assertEquals("name", sortOrder.getProperty());
        assertTrue(sortOrder.isAscending());
    }

    // ─── getCompany ──────────────────────────────────────────────────────────

    @Test
    void getCompany_delegatesToAccessServiceAndReturnsResponse() {
        Company c = makeCompany(COMPANY_ID, OWNER_ID, CompanyStatus.ACTIVE);
        when(companyAccessService.requireAnyAccess(COMPANY_ID, OWNER_ID)).thenReturn(c);

        CompanyResponse result = service.getCompany(COMPANY_ID, OWNER_ID);

        assertEquals(COMPANY_ID, result.getId());
    }

    // ─── getPublicCompany ─────────────────────────────────────────────────────

    @Test
    void getPublicCompany_activeCompany_returnsPublicResponse() {
        Company c = makeCompany(COMPANY_ID, OWNER_ID, CompanyStatus.ACTIVE);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(c));

        PublicCompanyResponse result = service.getPublicCompany(COMPANY_ID);

        assertEquals(COMPANY_ID, result.getId());
    }

    @Test
    void getPublicCompany_inactiveCompany_throwsResourceNotFound() {
        Company c = makeCompany(COMPANY_ID, OWNER_ID, CompanyStatus.INACTIVE);
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(c));

        assertThrows(ResourceNotFoundException.class,
                () -> service.getPublicCompany(COMPANY_ID));
    }

    @Test
    void getPublicCompany_notFound_throwsResourceNotFound() {
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getPublicCompany(COMPANY_ID));
    }

    // ─── createCompany ───────────────────────────────────────────────────────

    @Test
    void createCompany_userNotFound_throwsResourceNotFound() {
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.createCompany(OWNER_ID, makeCreateRequest("Acme")));
    }

    @Test
    void createCompany_duplicateName_throwsConflict() {
        User owner = makeUser(OWNER_ID);
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
        when(companyRepository.existsByNameAndOwnerId("Acme", OWNER_ID)).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> service.createCompany(OWNER_ID, makeCreateRequest("Acme")));
        verify(companyRepository, never()).save(any(Company.class));
    }

    @Test
    void createCompany_savesCompanyAndOwnerMembership() {
        User owner = makeUser(OWNER_ID);
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
        when(companyRepository.existsByNameAndOwnerId(any(), any())).thenReturn(false);
        when(companyRepository.save(any())).thenAnswer(inv -> {
            Company c = inv.getArgument(0);
            c.setId(COMPANY_ID);
            return c;
        });
        when(membershipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createCompany(OWNER_ID, makeCreateRequest("Acme"));

        verify(companyRepository).save(any(Company.class));
        ArgumentCaptor<CompanyMembership> mCap = ArgumentCaptor.forClass(CompanyMembership.class);
        verify(membershipRepository).save(mCap.capture());
        assertEquals(CompanyRole.OWNER, mCap.getValue().getRole());
        assertEquals(CompanyMembershipStatus.ACTIVE, mCap.getValue().getStatus());
        assertNotNull(mCap.getValue().getAcceptedAt());
    }

    @Test
    void createCompany_invalidatesAccessCache() {
        User owner = makeUser(OWNER_ID);
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
        when(companyRepository.existsByNameAndOwnerId(any(), any())).thenReturn(false);
        when(companyRepository.save(any())).thenAnswer(inv -> {
            Company c = inv.getArgument(0);
            c.setId(COMPANY_ID);
            return c;
        });
        when(membershipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.createCompany(OWNER_ID, makeCreateRequest("Acme"));

        verify(companyAccessService).invalidate(COMPANY_ID, OWNER_ID);
    }

    @Test
    void createCompany_setsAllRequestFields() {
        User owner = makeUser(OWNER_ID);
        when(userRepository.findById(OWNER_ID)).thenReturn(Optional.of(owner));
        when(companyRepository.existsByNameAndOwnerId(any(), any())).thenReturn(false);
        when(companyRepository.save(any())).thenAnswer(inv -> {
            Company c = inv.getArgument(0);
            c.setId(COMPANY_ID);
            return c;
        });
        when(membershipRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateCompanyRequest req = makeCreateRequest("TechCo");
        req.setCity("San Francisco");
        req.setCountry("US");
        req.setIndustry("Technology");
        service.createCompany(OWNER_ID, req);

        ArgumentCaptor<Company> cap = ArgumentCaptor.forClass(Company.class);
        verify(companyRepository).save(cap.capture());
        assertEquals("TechCo", cap.getValue().getName());
        assertEquals("San Francisco", cap.getValue().getCity());
        assertEquals("Technology", cap.getValue().getIndustry());
    }

    // ─── updateCompany ───────────────────────────────────────────────────────

    @Test
    void updateCompany_nameDuplicate_throwsConflict() {
        Company existing = makeCompany(COMPANY_ID, OWNER_ID, CompanyStatus.ACTIVE);
        existing.setName("OldName");
        when(companyAccessService.require(COMPANY_ID, OWNER_ID, CompanyCapability.MANAGE_COMPANY))
                .thenReturn(existing);
        when(companyRepository.existsByNameAndOwnerId("NewName", OWNER_ID)).thenReturn(true);

        UpdateCompanyRequest req = new UpdateCompanyRequest();
        req.setName("NewName");

        assertThrows(ConflictException.class,
                () -> service.updateCompany(COMPANY_ID, OWNER_ID, req));
    }

    @Test
    void updateCompany_sameNameAsExisting_doesNotCheckConflict() {
        Company existing = makeCompany(COMPANY_ID, OWNER_ID, CompanyStatus.ACTIVE);
        existing.setName("SameName");
        when(companyAccessService.require(COMPANY_ID, OWNER_ID, CompanyCapability.MANAGE_COMPANY))
                .thenReturn(existing);
        when(companyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateCompanyRequest req = new UpdateCompanyRequest();
        req.setName("SameName");

        service.updateCompany(COMPANY_ID, OWNER_ID, req);

        verify(companyRepository, never()).existsByNameAndOwnerId(any(), any());
    }

    @Test
    void updateCompany_updatesOnlyProvidedFields() {
        Company existing = makeCompany(COMPANY_ID, OWNER_ID, CompanyStatus.ACTIVE);
        existing.setCity("OldCity");
        when(companyAccessService.require(COMPANY_ID, OWNER_ID, CompanyCapability.MANAGE_COMPANY))
                .thenReturn(existing);
        when(companyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateCompanyRequest req = new UpdateCompanyRequest();
        req.setCity("NewCity");

        service.updateCompany(COMPANY_ID, OWNER_ID, req);

        ArgumentCaptor<Company> cap = ArgumentCaptor.forClass(Company.class);
        verify(companyRepository).save(cap.capture());
        assertEquals("NewCity", cap.getValue().getCity());
    }

    @Test
    void updateCompany_nullFieldsSkipped() {
        Company existing = makeCompany(COMPANY_ID, OWNER_ID, CompanyStatus.ACTIVE);
        existing.setCity("Preserved");
        when(companyAccessService.require(COMPANY_ID, OWNER_ID, CompanyCapability.MANAGE_COMPANY))
                .thenReturn(existing);
        when(companyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Empty request — all fields null
        service.updateCompany(COMPANY_ID, OWNER_ID, new UpdateCompanyRequest());

        ArgumentCaptor<Company> cap = ArgumentCaptor.forClass(Company.class);
        verify(companyRepository).save(cap.capture());
        assertEquals("Preserved", cap.getValue().getCity());
    }

    // ─── deleteCompany ───────────────────────────────────────────────────────

    @Test
    void deleteCompany_deletesCompanyAndInvalidatesCache() {
        Company existing = makeCompany(COMPANY_ID, OWNER_ID, CompanyStatus.ACTIVE);
        when(companyAccessService.require(COMPANY_ID, OWNER_ID, CompanyCapability.MANAGE_COMPANY))
                .thenReturn(existing);

        service.deleteCompany(COMPANY_ID, OWNER_ID);

        verify(companyRepository).delete(existing);
        verify(companyAccessService).invalidate(COMPANY_ID, OWNER_ID);
    }

    // ─── generateLogoUploadUrl ───────────────────────────────────────────────

    @Test
    void generateLogoUploadUrl_delegatesToStorageService() {
        Company existing = makeCompany(COMPANY_ID, OWNER_ID, CompanyStatus.ACTIVE);
        when(companyAccessService.require(COMPANY_ID, OWNER_ID, CompanyCapability.MANAGE_COMPANY))
                .thenReturn(existing);
        PresignUploadResponse expected = new PresignUploadResponse("https://s3.url", "https://cdn.url", "key", 300);
        when(storageService.generatePresignedUrl(eq(UploadFolder.COMPANY_LOGO), eq(OWNER_ID), eq("image/png")))
                .thenReturn(expected);

        PresignUploadResponse result = service.generateLogoUploadUrl(COMPANY_ID, OWNER_ID, "image/png");

        assertSame(expected, result);
    }

    // ─── getMyCompany ────────────────────────────────────────────────────────

    @Test
    void getMyCompany_returnsFirstAccessibleCompany() {
        Company c = makeCompany(COMPANY_ID, OWNER_ID, CompanyStatus.ACTIVE);
        when(companyAccessService.listAccessibleCompanies(OWNER_ID)).thenReturn(List.of(c));

        CompanyResponse result = service.getMyCompany(OWNER_ID);

        assertEquals(COMPANY_ID, result.getId());
    }

    @Test
    void getMyCompany_noCompanies_throwsResourceNotFound() {
        when(companyAccessService.listAccessibleCompanies(OWNER_ID)).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getMyCompany(OWNER_ID));
    }

    // ─── getCompaniesByIds ───────────────────────────────────────────────────

    @Test
    void getCompaniesByIds_filtersToMatchingAccessibleCompanies() {
        Company c1 = makeCompany(COMPANY_ID, OWNER_ID, CompanyStatus.ACTIVE);
        Company c2 = makeCompany(TestIds.uuid(99), OWNER_ID, CompanyStatus.ACTIVE);
        when(companyAccessService.listAccessibleCompanies(OWNER_ID)).thenReturn(List.of(c1, c2));

        List<CompanyResponse> result = service.getCompaniesByIds(List.of(COMPANY_ID), OWNER_ID);

        assertEquals(1, result.size());
        assertEquals(COMPANY_ID, result.get(0).getId());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private User makeUser(UUID id) {
        User u = new User();
        u.setId(id);
        u.setEmail("user-" + id + "@test.com");
        return u;
    }

    private Company makeCompany(UUID id, UUID ownerId, CompanyStatus status) {
        User owner = makeUser(ownerId);
        Company c = new Company();
        c.setId(id);
        c.setOwner(owner);
        c.setName("Test Company");
        c.setStatus(status);
        return c;
    }

    private CreateCompanyRequest makeCreateRequest(String name) {
        CreateCompanyRequest req = new CreateCompanyRequest();
        req.setName(name);
        return req;
    }
}
