package backend.services.impl.pricing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.models.core.TaxRate;
import backend.models.enums.TaxSource;
import backend.repositories.TaxRateRepository;
import backend.services.intf.pricing.TaxService;
import backend.services.pricing.ResolvedTaxRate;
import backend.services.pricing.TaxAmounts;
import backend.services.pricing.TaxDestination;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class TaxServiceImpl implements TaxService {

    private final TaxRateRepository taxRateRepository;
    private final BigDecimal defaultRate;
    private final boolean fallbackShippingTaxable;

    public TaxServiceImpl(
            TaxRateRepository taxRateRepository,
            @Value("${app.tax.default-rate:0.00}") BigDecimal defaultRate,
            @Value("${app.tax.fallback.shipping-taxable:false}") boolean fallbackShippingTaxable) {
        this.taxRateRepository = taxRateRepository;
        // Clamp to a sane [0,1) band so a misconfigured property can't charge a >100% rate.
        BigDecimal r = defaultRate == null ? BigDecimal.ZERO : defaultRate;
        if (r.signum() < 0) r = BigDecimal.ZERO;
        if (r.compareTo(BigDecimal.ONE) >= 0) r = BigDecimal.ZERO;
        this.defaultRate = r;
        this.fallbackShippingTaxable = fallbackShippingTaxable;
    }

    @Override
    @Transactional(readOnly = true)
    public ResolvedTaxRate resolve(TaxDestination dest) {
        if (dest == null || dest.country() == null || dest.country().isBlank()) {
            return ResolvedTaxRate.none();
        }
        String country = dest.country().trim().toUpperCase();
        String state = dest.state() == null ? "" : dest.state().trim().toUpperCase();
        String postalCode = dest.postalCode() == null ? "" : dest.postalCode().trim();

        List<TaxRate> matches = taxRateRepository.findBestMatch(country, state, postalCode, PageRequest.of(0, 1));
        if (matches.isEmpty()) {
            return new ResolvedTaxRate(defaultRate, fallbackShippingTaxable, TaxSource.CONFIG_FALLBACK, null);
        }
        TaxRate match = matches.get(0);
        return new ResolvedTaxRate(match.getRate(), match.isShippingTaxable(), classify(match, state, postalCode),
                match.getId());
    }

    @Override
    public TaxAmounts compute(BigDecimal taxableSubtotal, BigDecimal shippingAmount, ResolvedTaxRate rate) {
        ResolvedTaxRate r = rate == null ? ResolvedTaxRate.none() : rate;
        BigDecimal sub = taxableSubtotal == null ? BigDecimal.ZERO : taxableSubtotal;
        BigDecimal ship = shippingAmount == null ? BigDecimal.ZERO : shippingAmount;

        if (r.rate() == null || r.rate().signum() <= 0) {
            return TaxAmounts.zero(r.source());
        }
        BigDecimal taxable = sub;
        if (r.shippingTaxable()) {
            taxable = taxable.add(ship);
        }
        taxable = taxable.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal tax = taxable.multiply(r.rate()).setScale(2, RoundingMode.HALF_UP);
        return new TaxAmounts(taxable, r.rate(), tax, r.source(), r.taxRateId());
    }

    /** Determines which jurisdiction granularity the matched row represents. */
    private TaxSource classify(TaxRate match, String requestedState, String requestedPostal) {
        boolean stateMatches = match.getState().equals(requestedState) && !match.getState().isEmpty();
        if (stateMatches && match.getPostalCode().equals(requestedPostal) && !match.getPostalCode().isEmpty()) {
            return TaxSource.DESTINATION_MATCH;
        }
        if (stateMatches) {
            return TaxSource.STATE_DEFAULT;
        }
        return TaxSource.COUNTRY_DEFAULT;
    }
}
