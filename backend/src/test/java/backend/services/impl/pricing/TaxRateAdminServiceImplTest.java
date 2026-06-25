package backend.services.impl.pricing;

import backend.dtos.requests.tax.CreateTaxRateRequest;
import backend.dtos.requests.tax.UpdateTaxRateRequest;
import backend.dtos.responses.tax.TaxRateResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.TaxRate;
import backend.repositories.TaxRateRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TaxRateAdminServiceImplTest {

    private final TaxRateRepository repo = mock(TaxRateRepository.class);
    private final TaxRateAdminServiceImpl service = new TaxRateAdminServiceImpl(repo);

    private static CreateTaxRateRequest createReq(String country, String state, String rate) {
        CreateTaxRateRequest r = new CreateTaxRateRequest();
        r.setCountry(country);
        r.setState(state);
        r.setRate(new BigDecimal(rate));
        return r;
    }

    @Test
    void create_normalizesJurisdictionAndDefaultsActive() {
        when(repo.findByCountryAndStateAndPostalCode(any(), any(), any())).thenReturn(Optional.empty());
        when(repo.save(any(TaxRate.class))).thenAnswer(inv -> inv.getArgument(0));

        TaxRateResponse resp = service.createRate(createReq("us", "ca", "0.07250"));

        assertEquals("US", resp.getCountry());
        assertEquals("CA", resp.getState());
        assertEquals("", resp.getPostalCode());
        assertTrue(resp.isActive());
    }

    @Test
    void create_duplicateJurisdiction_throwsConflict() {
        when(repo.findByCountryAndStateAndPostalCode(eq("US"), eq("CA"), eq("")))
                .thenReturn(Optional.of(new TaxRate()));
        assertThrows(ConflictException.class, () -> service.createRate(createReq("US", "CA", "0.07250")));
        verify(repo, never()).save(any());
    }

    @Test
    void create_rateAtOrAboveOne_throwsBadRequest() {
        when(repo.findByCountryAndStateAndPostalCode(any(), any(), any())).thenReturn(Optional.empty());
        assertThrows(BadRequestException.class, () -> service.createRate(createReq("US", "CA", "1.00")));
    }

    @Test
    void update_missingRate_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.updateRate(id, new UpdateTaxRateRequest()));
    }

    @Test
    void update_appliesOnlyNonNullFields() {
        UUID id = UUID.randomUUID();
        TaxRate existing = new TaxRate();
        existing.setCountry("US");
        existing.setState("CA");
        existing.setPostalCode("");
        existing.setRate(new BigDecimal("0.07250"));
        existing.setShippingTaxable(false);
        existing.setActive(true);
        when(repo.findById(id)).thenReturn(Optional.of(existing));
        when(repo.save(any(TaxRate.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateTaxRateRequest req = new UpdateTaxRateRequest();
        req.setActive(false);
        TaxRateResponse resp = service.updateRate(id, req);

        assertFalse(resp.isActive());
        assertEquals(new BigDecimal("0.07250"), resp.getRate()); // unchanged
    }

    @Test
    void delete_existing_softDeactivatesInsteadOfRemoving() {
        UUID id = UUID.randomUUID();
        TaxRate existing = new TaxRate();
        existing.setActive(true);
        when(repo.findById(id)).thenReturn(Optional.of(existing));
        when(repo.save(any(TaxRate.class))).thenAnswer(inv -> inv.getArgument(0));

        service.deleteRate(id);

        assertFalse(existing.isActive());
        verify(repo, never()).delete(any(TaxRate.class));
        verify(repo).save(existing);
    }
}
