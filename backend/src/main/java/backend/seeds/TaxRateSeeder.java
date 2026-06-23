package backend.seeds;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import backend.models.core.TaxRate;
import backend.repositories.TaxRateRepository;

import java.math.BigDecimal;

/**
 * Seeds baseline US sales-tax rates. Unlike the dev fixtures this is reference data the tax
 * resolver needs in every profile, so it runs unconditionally and is idempotent — it only
 * inserts when the table is empty.
 *
 * <p>Includes a country-level default ({@code state = ""}), several state rates, and at least one
 * jurisdiction that taxes shipping (TX) so both branches are exercisable out of the box.
 */
@Slf4j
@Component
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
