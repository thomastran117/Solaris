package backend.services.impl.pricing;

import backend.models.core.TaxRate;
import backend.models.enums.TaxSource;
import backend.repositories.TaxRateRepository;
import backend.services.pricing.ResolvedTaxRate;
import backend.services.pricing.TaxAmounts;
import backend.services.pricing.TaxDestination;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaxServiceImplTest {

    private final TaxRateRepository repo = mock(TaxRateRepository.class);

    private TaxServiceImpl service() {
        return new TaxServiceImpl(repo, new BigDecimal("0.00"), false);
    }

    private TaxServiceImpl serviceWithFallback(String rate, boolean shippingTaxable) {
        return new TaxServiceImpl(repo, new BigDecimal(rate), shippingTaxable);
    }

    private static TaxRate row(String country, String state, String postal, String rate, boolean shipTax) {
        TaxRate t = new TaxRate();
        t.setId(java.util.UUID.randomUUID());
        t.setCountry(country);
        t.setState(state);
        t.setPostalCode(postal);
        t.setRate(new BigDecimal(rate));
        t.setShippingTaxable(shipTax);
        t.setActive(true);
        return t;
    }

    // -------------------- resolve --------------------

    @Test
    void resolve_nullDestination_returnsNone() {
        ResolvedTaxRate r = service().resolve(null);
        assertEquals(TaxSource.NONE, r.source());
        assertEquals(BigDecimal.ZERO, r.rate());
        assertNull(r.taxRateId());
    }

    @Test
    void resolve_blankCountry_returnsNone() {
        assertEquals(TaxSource.NONE, service().resolve(new TaxDestination("  ", "CA", "94105")).source());
    }

    @Test
    void resolve_postalOverrideChosenWhenPresent() {
        TaxRate zip = row("US", "CA", "94105", "0.08625", false);
        when(repo.findBestMatch(eq("US"), eq("CA"), eq("94105"), any(Pageable.class)))
                .thenReturn(List.of(zip));
        ResolvedTaxRate r = service().resolve(new TaxDestination("US", "CA", "94105"));
        assertEquals(TaxSource.DESTINATION_MATCH, r.source());
        assertEquals(new BigDecimal("0.08625"), r.rate());
        assertEquals(zip.getId(), r.taxRateId());
    }

    @Test
    void resolve_unknownZip_fallsBackToStateRate() {
        TaxRate stateRow = row("US", "CA", "", "0.07250", false);
        when(repo.findBestMatch(eq("US"), eq("CA"), eq("99999"), any(Pageable.class)))
                .thenReturn(List.of(stateRow));
        ResolvedTaxRate r = service().resolve(new TaxDestination("US", "CA", "99999"));
        assertEquals(TaxSource.STATE_DEFAULT, r.source());
        assertEquals(new BigDecimal("0.07250"), r.rate());
    }

    @Test
    void resolve_unknownState_fallsBackToCountryDefault() {
        TaxRate countryRow = row("US", "", "", "0.00000", false);
        when(repo.findBestMatch(eq("US"), eq("ZZ"), eq(""), any(Pageable.class)))
                .thenReturn(List.of(countryRow));
        ResolvedTaxRate r = service().resolve(new TaxDestination("US", "ZZ", ""));
        assertEquals(TaxSource.COUNTRY_DEFAULT, r.source());
    }

    @Test
    void resolve_noRowMatches_usesConfigFallback() {
        when(repo.findBestMatch(any(), any(), any(), any(Pageable.class))).thenReturn(List.of());
        ResolvedTaxRate r = serviceWithFallback("0.05000", true)
                .resolve(new TaxDestination("US", "CA", ""));
        assertEquals(TaxSource.CONFIG_FALLBACK, r.source());
        assertEquals(new BigDecimal("0.05000"), r.rate());
        assertTrue(r.shippingTaxable());
        assertNull(r.taxRateId());
    }

    @Test
    void resolve_lowercaseInput_isNormalized() {
        TaxRate stateRow = row("US", "CA", "", "0.07250", false);
        when(repo.findBestMatch(eq("US"), eq("CA"), eq(""), any(Pageable.class)))
                .thenReturn(List.of(stateRow));
        ResolvedTaxRate r = service().resolve(new TaxDestination("us", "ca", ""));
        assertEquals(TaxSource.STATE_DEFAULT, r.source());
    }

    // -------------------- compute --------------------

    @Test
    void compute_taxesSubtotalOnly_whenShippingNotTaxable() {
        ResolvedTaxRate rate = new ResolvedTaxRate(new BigDecimal("0.10"), false, TaxSource.STATE_DEFAULT, null);
        TaxAmounts t = service().compute(new BigDecimal("100.00"), new BigDecimal("10.00"), rate);
        assertEquals(new BigDecimal("100.00"), t.taxableAmount());
        assertEquals(new BigDecimal("10.00"), t.taxAmount());
    }

    @Test
    void compute_taxesShipping_whenShippingTaxable() {
        ResolvedTaxRate rate = new ResolvedTaxRate(new BigDecimal("0.10"), true, TaxSource.STATE_DEFAULT, null);
        TaxAmounts t = service().compute(new BigDecimal("100.00"), new BigDecimal("10.00"), rate);
        assertEquals(new BigDecimal("110.00"), t.taxableAmount());
        assertEquals(new BigDecimal("11.00"), t.taxAmount());
    }

    @Test
    void compute_roundsHalfUpToTwoDecimals() {
        ResolvedTaxRate rate = new ResolvedTaxRate(new BigDecimal("0.08875"), false, TaxSource.STATE_DEFAULT, null);
        TaxAmounts t = service().compute(new BigDecimal("33.33"), BigDecimal.ZERO, rate);
        // 33.33 * 0.08875 = 2.9580375 -> 2.96
        assertEquals(new BigDecimal("2.96"), t.taxAmount());
    }

    @Test
    void compute_zeroRate_returnsZeroTaxKeepingSource() {
        ResolvedTaxRate rate = new ResolvedTaxRate(BigDecimal.ZERO, false, TaxSource.COUNTRY_DEFAULT, null);
        TaxAmounts t = service().compute(new BigDecimal("100.00"), BigDecimal.ZERO, rate);
        assertEquals(0, t.taxAmount().compareTo(BigDecimal.ZERO));
        assertEquals(TaxSource.COUNTRY_DEFAULT, t.source());
    }

    @Test
    void compute_nullAmounts_treatedAsZero() {
        ResolvedTaxRate rate = new ResolvedTaxRate(new BigDecimal("0.10"), true, TaxSource.STATE_DEFAULT, null);
        TaxAmounts t = service().compute(null, null, rate);
        assertEquals(0, t.taxAmount().compareTo(BigDecimal.ZERO));
    }
}
