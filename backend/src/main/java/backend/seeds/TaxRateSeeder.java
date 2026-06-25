package backend.seeds;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import backend.models.core.TaxRate;
import backend.repositories.TaxRateRepository;

import java.math.BigDecimal;

/**
 * Seeds <em>illustrative</em> US sales-tax rates for local development and tests only.
 *
 * <p><b>Dev-only by design.</b> These are state-base rates (CA 7.25%, NY 4%, TX 6.25%, …), not the
 * combined state+county+city rates a customer legally owes — seeding them in production would silently
 * undercharge. Production starts with an empty table: with no matching row the resolver returns the
 * configured fallback ({@code app.tax.default-rate}, default 0%), and operators configure real,
 * jurisdiction-accurate rates through {@code POST /admin/tax-rates}.
 *
 * <p>Idempotent: only inserts when the table is empty.
 */
@Slf4j
@Component
@Profile("dev")
@Order(0)
@RequiredArgsConstructor
public class TaxRateSeeder implements ApplicationRunner {

    private final TaxRateRepository taxRateRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (taxRateRepository.count() > 0) return;

        // Country-level default: applies when no state row matches.
        rate("US", "", "0.00000", false, "US default (no state-level rate configured)");

        // Representative state rates (shipping non-taxable unless noted).
        rate("US", "CA", "0.07250", false, "California state sales tax");
        rate("US", "NY", "0.04000", false, "New York state sales tax");
        rate("US", "WA", "0.06500", false, "Washington state sales tax");
        rate("US", "FL", "0.06000", false, "Florida state sales tax");
        rate("US", "TX", "0.06250", true,  "Texas state sales tax (taxes shipping)");

        // No-sales-tax states, explicit so they resolve to STATE_DEFAULT rather than country default.
        rate("US", "OR", "0.00000", false, "Oregon — no state sales tax");
        rate("US", "DE", "0.00000", false, "Delaware — no state sales tax");

        log.info("[TaxRateSeeder] Seeded {} tax rates", taxRateRepository.count());
    }

    private void rate(String country, String state, String rate, boolean shippingTaxable, String description) {
        TaxRate t = new TaxRate();
        t.setCountry(country);
        t.setState(state);
        t.setPostalCode("");
        t.setRate(new BigDecimal(rate));
        t.setShippingTaxable(shippingTaxable);
        t.setActive(true);
        t.setDescription(description);
        taxRateRepository.save(t);
    }
}
