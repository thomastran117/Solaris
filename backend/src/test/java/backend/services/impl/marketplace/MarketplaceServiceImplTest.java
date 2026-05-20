package backend.services.impl.marketplace;

import backend.dtos.requests.marketplace.CreateMarketplaceRequest;
import backend.dtos.requests.marketplace.UpdateMarketplaceRequest;
import backend.dtos.responses.marketplace.MarketplaceProfileResponse;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Company;
import backend.models.core.MarketplaceProfile;
import backend.models.core.User;
import backend.models.enums.CompanyCapability;
import backend.models.enums.PayoutSchedule;
import backend.repositories.MarketplaceProfileRepository;
import backend.services.intf.company.CompanyAccessService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketplaceServiceImplTest {

    private static final UUID OWNER_ID = TestIds.uuid(1);
    private static final UUID COMPANY_ID = TestIds.uuid(2);
    private static final UUID MARKETPLACE_ID = TestIds.uuid(3);

    private MarketplaceProfileRepository marketplaceProfileRepository;
    private CompanyAccessService companyAccessService;
    private MarketplaceServiceImpl service;

    @BeforeEach
    void setUp() {
        marketplaceProfileRepository = mock(MarketplaceProfileRepository.class);
        companyAccessService = mock(CompanyAccessService.class);
        service = new MarketplaceServiceImpl(marketplaceProfileRepository, companyAccessService);
        when(marketplaceProfileRepository.save(any(MarketplaceProfile.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createMarketplace_rejectsExistingSlug() {
        when(companyAccessService.require(any(), any(), any())).thenReturn(company());
        when(marketplaceProfileRepository.existsByCompanyId(COMPANY_ID)).thenReturn(false);
        when(marketplaceProfileRepository.existsBySlug("market-one")).thenReturn(true);

        CreateMarketplaceRequest request = new CreateMarketplaceRequest();
        request.setSlug("market-one");

        assertThrows(ConflictException.class,
                () -> service.createMarketplace(OWNER_ID, COMPANY_ID, request));
    }

    @Test
    void createMarketplace_rejectsExistingCompanyProfile() {
        when(companyAccessService.require(any(), any(), any())).thenReturn(company());
        when(marketplaceProfileRepository.existsByCompanyId(COMPANY_ID)).thenReturn(true);

        CreateMarketplaceRequest request = new CreateMarketplaceRequest();
        request.setSlug("market-one");

        assertThrows(ConflictException.class,
                () -> service.createMarketplace(OWNER_ID, COMPANY_ID, request));
    }

    @Test
    void createMarketplace_defaultsCurrencyAndMapsResponse() {
        when(companyAccessService.require(any(), any(), any())).thenReturn(company());
        when(marketplaceProfileRepository.existsByCompanyId(COMPANY_ID)).thenReturn(false);
        when(marketplaceProfileRepository.existsBySlug("market-one")).thenReturn(false);
        when(marketplaceProfileRepository.save(any(MarketplaceProfile.class))).thenAnswer(inv -> {
            MarketplaceProfile profile = inv.getArgument(0);
            profile.setId(MARKETPLACE_ID);
            profile.setCreatedAt(Instant.parse("2026-05-19T00:00:00Z"));
            profile.setUpdatedAt(Instant.parse("2026-05-19T00:00:00Z"));
            return profile;
        });

        CreateMarketplaceRequest request = new CreateMarketplaceRequest();
        request.setSlug("market-one");
        request.setPayoutSchedule(PayoutSchedule.MONTHLY);
        request.setHoldPeriodDays(14);
        request.setDefaultCurrency(null);
        request.setAcceptingApplications(false);

        MarketplaceProfileResponse response = service.createMarketplace(OWNER_ID, COMPANY_ID, request);

        assertEquals(MARKETPLACE_ID, response.getId());
        assertEquals("USD", response.getDefaultCurrency());
        assertEquals("MONTHLY", response.getPayoutSchedule());
        assertEquals(false, response.isAcceptingApplications());
    }

    @Test
    void getMarketplace_returnsMappedResponse() {
        when(marketplaceProfileRepository.findById(MARKETPLACE_ID)).thenReturn(Optional.of(profile()));

        MarketplaceProfileResponse response = service.getMarketplace(MARKETPLACE_ID);

        assertEquals(MARKETPLACE_ID, response.getId());
        assertEquals(COMPANY_ID, response.getCompanyId());
        assertEquals("ShopWave", response.getCompanyName());
        assertEquals("market-one", response.getSlug());
    }

    @Test
    void getMarketplace_missingProfileThrowsNotFound() {
        when(marketplaceProfileRepository.findById(MARKETPLACE_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getMarketplace(MARKETPLACE_ID));
    }

    @Test
    void updateMarketplace_updatesOnlyProvidedFields() {
        MarketplaceProfile profile = profile();
        when(marketplaceProfileRepository.findById(MARKETPLACE_ID)).thenReturn(Optional.of(profile));

        UpdateMarketplaceRequest request = new UpdateMarketplaceRequest();
        request.setHoldPeriodDays(21);
        request.setAcceptingApplications(false);

        MarketplaceProfileResponse response = service.updateMarketplace(MARKETPLACE_ID, OWNER_ID, request);

        assertEquals("WEEKLY", response.getPayoutSchedule());
        assertEquals(21, response.getHoldPeriodDays());
        assertEquals(false, response.isAcceptingApplications());
        verify(companyAccessService).require(COMPANY_ID, OWNER_ID, CompanyCapability.MANAGE_PRODUCTS);
    }

    @Test
    void updateMarketplace_missingProfileThrowsNotFound() {
        when(marketplaceProfileRepository.findById(MARKETPLACE_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.updateMarketplace(MARKETPLACE_ID, OWNER_ID, new UpdateMarketplaceRequest()));
    }

    private Company company() {
        User owner = new User();
        owner.setId(OWNER_ID);
        owner.setEmail("owner@test.com");

        Company company = new Company();
        company.setId(COMPANY_ID);
        company.setOwner(owner);
        company.setName("ShopWave");
        return company;
    }

    private MarketplaceProfile profile() {
        MarketplaceProfile profile = new MarketplaceProfile();
        profile.setId(MARKETPLACE_ID);
        profile.setCompany(company());
        profile.setSlug("market-one");
        profile.setPayoutSchedule(PayoutSchedule.WEEKLY);
        profile.setHoldPeriodDays(7);
        profile.setDefaultCurrency("USD");
        profile.setAcceptingApplications(true);
        profile.setCreatedAt(Instant.parse("2026-05-19T00:00:00Z"));
        profile.setUpdatedAt(Instant.parse("2026-05-19T00:00:00Z"));
        return profile;
    }
}
