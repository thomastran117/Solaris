package backend.services.impl.returns;

import backend.dtos.requests.return_.CreateReturnLocationRequest;
import backend.dtos.requests.return_.UpdateReturnLocationRequest;
import backend.dtos.responses.return_.ReturnLocationResponse;
import backend.exceptions.http.ConflictException;
import backend.models.core.Company;
import backend.models.core.CompanyReturnLocation;
import backend.models.enums.CompanyCapability;
import backend.repositories.CompanyReturnLocationRepository;
import backend.services.intf.company.CompanyAccessService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import backend.exceptions.http.ResourceNotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReturnLocationServiceImplTest {

    private static final UUID COMPANY_ID = TestIds.uuid(1);
    private static final UUID OWNER_ID = TestIds.uuid(2);
    private static final UUID LOCATION_ID = TestIds.uuid(3);

    private CompanyReturnLocationRepository locationRepository;
    private CompanyAccessService companyAccessService;
    private ReturnLocationServiceImpl service;

    @BeforeEach
    void setUp() {
        locationRepository = mock(CompanyReturnLocationRepository.class);
        companyAccessService = mock(CompanyAccessService.class);
        service = new ReturnLocationServiceImpl(locationRepository, companyAccessService);
    }

    @Test
    void createReturnLocation_primaryClearsExistingPrimaryAndSaves() {
        when(companyAccessService.require(COMPANY_ID, OWNER_ID, CompanyCapability.FULFILL_ORDERS)).thenReturn(company());
        when(locationRepository.save(any(CompanyReturnLocation.class))).thenAnswer(inv -> {
            CompanyReturnLocation loc = inv.getArgument(0);
            loc.setId(LOCATION_ID);
            loc.setCreatedAt(Instant.parse("2026-05-19T00:00:00Z"));
            loc.setUpdatedAt(Instant.parse("2026-05-19T00:00:00Z"));
            return loc;
        });

        ReturnLocationResponse response = service.createReturnLocation(
                COMPANY_ID,
                OWNER_ID,
                new CreateReturnLocationRequest("123 Warehouse Rd", "Toronto", "CA", "M5V1A1", "Main Warehouse", true)
        );

        verify(locationRepository).clearPrimaryForCompany(COMPANY_ID);
        assertEquals(LOCATION_ID, response.id());
        assertEquals("Main Warehouse", response.name());
        assertEquals(true, response.primary());
    }

    @Test
    void getReturnLocations_mapsRepositoryRows() {
        when(locationRepository.findAllByCompanyId(COMPANY_ID)).thenReturn(List.of(location(true)));

        List<ReturnLocationResponse> responses = service.getReturnLocations(COMPANY_ID, OWNER_ID);

        verify(companyAccessService).require(COMPANY_ID, OWNER_ID, CompanyCapability.FULFILL_ORDERS);
        assertEquals(1, responses.size());
        assertEquals(LOCATION_ID, responses.get(0).id());
    }

    @Test
    void updateReturnLocation_settingPrimaryClearsOldPrimary() {
        when(locationRepository.findByIdAndCompanyId(LOCATION_ID, COMPANY_ID)).thenReturn(Optional.of(location(false)));
        when(locationRepository.save(any(CompanyReturnLocation.class))).thenAnswer(inv -> inv.getArgument(0));

        ReturnLocationResponse response = service.updateReturnLocation(
                LOCATION_ID,
                COMPANY_ID,
                OWNER_ID,
                new UpdateReturnLocationRequest("456 Returns Ave", "Montreal", "CA", "H2Y1C6", "East", true)
        );

        verify(companyAccessService).require(COMPANY_ID, OWNER_ID, CompanyCapability.FULFILL_ORDERS);
        verify(locationRepository).clearPrimaryForCompany(COMPANY_ID);
        assertEquals("Montreal", response.city());
        assertEquals(true, response.primary());
    }

    @Test
    void deleteReturnLocation_lastLocationThrowsConflict() {
        when(locationRepository.findByIdAndCompanyId(LOCATION_ID, COMPANY_ID)).thenReturn(Optional.of(location(true)));
        when(locationRepository.countByCompanyId(COMPANY_ID)).thenReturn(1L);

        assertThrows(ConflictException.class,
                () -> service.deleteReturnLocation(LOCATION_ID, COMPANY_ID, OWNER_ID));
    }

    @Test
    void deleteReturnLocation_existingLocationDeletes() {
        CompanyReturnLocation location = location(true);
        when(locationRepository.findByIdAndCompanyId(LOCATION_ID, COMPANY_ID)).thenReturn(Optional.of(location));
        when(locationRepository.countByCompanyId(COMPANY_ID)).thenReturn(2L);

        service.deleteReturnLocation(LOCATION_ID, COMPANY_ID, OWNER_ID);

        verify(locationRepository).delete(location);
    }

    @Test
    void createReturnLocation_nonPrimary_doesNotClearExistingPrimary() {
        when(companyAccessService.require(COMPANY_ID, OWNER_ID, CompanyCapability.FULFILL_ORDERS)).thenReturn(company());
        when(locationRepository.save(any(CompanyReturnLocation.class))).thenAnswer(inv -> {
            CompanyReturnLocation loc = inv.getArgument(0);
            loc.setId(LOCATION_ID);
            loc.setCreatedAt(Instant.parse("2026-05-19T00:00:00Z"));
            loc.setUpdatedAt(Instant.parse("2026-05-19T00:00:00Z"));
            return loc;
        });

        service.createReturnLocation(
                COMPANY_ID,
                OWNER_ID,
                new CreateReturnLocationRequest("123 Warehouse Rd", "Toronto", "CA", "M5V1A1", "Secondary", false)
        );

        verify(locationRepository, never()).clearPrimaryForCompany(any());
    }

    @Test
    void updateReturnLocation_notFound_throwsResourceNotFound() {
        when(locationRepository.findByIdAndCompanyId(LOCATION_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.updateReturnLocation(LOCATION_ID, COMPANY_ID, OWNER_ID,
                        new UpdateReturnLocationRequest(null, null, null, null, "Updated", null)));
    }

    @Test
    void updateReturnLocation_setPrimaryFalse_unsetsPrimary() {
        CompanyReturnLocation loc = location(true);
        when(locationRepository.findByIdAndCompanyId(LOCATION_ID, COMPANY_ID)).thenReturn(Optional.of(loc));
        when(locationRepository.save(any(CompanyReturnLocation.class))).thenAnswer(inv -> inv.getArgument(0));

        ReturnLocationResponse response = service.updateReturnLocation(
                LOCATION_ID, COMPANY_ID, OWNER_ID,
                new UpdateReturnLocationRequest(null, null, null, null, null, false));

        assertFalse(response.primary());
        verify(locationRepository, never()).clearPrimaryForCompany(any());
    }

    @Test
    void updateReturnLocation_alreadyPrimary_settingPrimaryTrueDoesNotClear() {
        CompanyReturnLocation loc = location(true); // already primary
        when(locationRepository.findByIdAndCompanyId(LOCATION_ID, COMPANY_ID)).thenReturn(Optional.of(loc));
        when(locationRepository.save(any(CompanyReturnLocation.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateReturnLocation(LOCATION_ID, COMPANY_ID, OWNER_ID,
                new UpdateReturnLocationRequest(null, null, null, null, null, true));

        verify(locationRepository, never()).clearPrimaryForCompany(any());
    }

    @Test
    void updateReturnLocation_nullFields_onlyChangedFieldsUpdated() {
        CompanyReturnLocation loc = location(false);
        loc.setCity("OriginalCity");
        when(locationRepository.findByIdAndCompanyId(LOCATION_ID, COMPANY_ID)).thenReturn(Optional.of(loc));
        when(locationRepository.save(any(CompanyReturnLocation.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateReturnLocation(LOCATION_ID, COMPANY_ID, OWNER_ID,
                new UpdateReturnLocationRequest(null, null, null, null, null, null));

        assertEquals("OriginalCity", loc.getCity());
    }

    @Test
    void deleteReturnLocation_notFound_throwsResourceNotFound() {
        when(locationRepository.findByIdAndCompanyId(LOCATION_ID, COMPANY_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.deleteReturnLocation(LOCATION_ID, COMPANY_ID, OWNER_ID));
    }

    private Company company() {
        Company company = new Company();
        company.setId(COMPANY_ID);
        company.setName("Vendor Co");
        return company;
    }

    private CompanyReturnLocation location(boolean primary) {
        CompanyReturnLocation location = new CompanyReturnLocation();
        location.setId(LOCATION_ID);
        location.setCompany(company());
        location.setName("Main Warehouse");
        location.setAddress("123 Warehouse Rd");
        location.setCity("Toronto");
        location.setCountry("CA");
        location.setPostalCode("M5V1A1");
        location.setPrimary(primary);
        location.setCreatedAt(Instant.parse("2026-05-19T00:00:00Z"));
        location.setUpdatedAt(Instant.parse("2026-05-19T00:00:00Z"));
        return location;
    }
}
