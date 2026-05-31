package backend.seeds;

import backend.models.core.Product;
import backend.models.core.ShippingAddress;
import backend.models.core.Subscription;
import backend.models.core.SubscriptionItem;
import backend.models.enums.BillingInterval;
import backend.models.enums.SubscriptionStatus;
import backend.repositories.SubscriptionRepository;
import backend.seeds.CompanySeeder.SeededCompanies;
import backend.seeds.UserSeeder.SeededUsers;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class SubscriptionSeeder {

    private final SubscriptionRepository subscriptionRepository;

    public void seed(SeededUsers users, SeededCompanies companies, List<Product> wellness) {
        if (subscriptionRepository.findByStripeSubscriptionId("sub_test_alice_protein").isPresent()) return;

        Product protein = wellness.stream()
                .filter(p -> p.getName().contains("Whey Protein"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Whey Protein product not found"));

        Instant now = Instant.now();

        Subscription sub = new Subscription();
        sub.setUser(users.alice());
        sub.setCompany(companies.wellness());
        sub.setStripeSubscriptionId("sub_test_alice_protein");
        sub.setStripeCustomerId("cus_test_alice");
        sub.setStripePriceId("price_test_protein_monthly");
        sub.setStripePaymentMethodId("pm_test_alice_4242");
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setBillingInterval(BillingInterval.MONTH);
        sub.setIntervalCount(1);
        sub.setCurrentPeriodStart(now.minus(15, ChronoUnit.DAYS));
        sub.setCurrentPeriodEnd(now.plus(15, ChronoUnit.DAYS));
        sub.setNextBillingAt(now.plus(15, ChronoUnit.DAYS));
        sub.setCurrency("USD");
        sub.setUnitAmountCents(protein.getPrice().movePointRight(2).longValue());
        sub.setVersion(0L);

        ShippingAddress addr = new ShippingAddress();
        addr.setStreet("123 Maple St");
        addr.setCity("Seattle");
        addr.setPostalCode("98101");
        addr.setCountry("US");
        sub.setShippingAddress(addr);

        SubscriptionItem si = new SubscriptionItem();
        si.setSubscription(sub);
        si.setProduct(protein);
        si.setQuantity(1);
        si.setUnitPriceCents(protein.getPrice().movePointRight(2).longValue());
        si.setStripeSubscriptionItemId("si_test_alice_protein");
        sub.getItems().add(si);

        subscriptionRepository.save(sub);
    }
}
