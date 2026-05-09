package backend.seeds;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import backend.repositories.UserRepository;
import backend.repositories.CompanyRepository;
import backend.repositories.ProductRepository;

@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final ProductRepository productRepository;

    private final UserSeeder userSeeder;
    private final CompanySeeder companySeeder;
    private final TechGadgetsProductSeeder techSeeder;
    private final StyleHubProductSeeder styleSeeder;
    private final WellnessWorldProductSeeder wellnessSeeder;
    private final HomeNestProductSeeder homeSeeder;
    private final SportZoneProductSeeder sportSeeder;
    private final ReviewSeeder reviewSeeder;
    private final LoyaltySeeder loyaltySeeder;
    private final PricingEngineSeeder pricingSeeder;
    private final BundleSeeder bundleSeeder;
    private final AddressSeeder addressSeeder;
    private final SavedListSeeder savedListSeeder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.findByEmail("admin@shopwave.dev").isPresent()) return;

        var users     = userSeeder.seed();
        var companies = companySeeder.seed(users);

        var techProducts     = techSeeder.seed(companies.tech());
        var styleProducts    = styleSeeder.seed(companies.style());
        var wellnessProducts = wellnessSeeder.seed(companies.wellness());
        var homeProducts     = homeSeeder.seed(companies.home());
        var sportProducts    = sportSeeder.seed(companies.sport());

        reviewSeeder.seed(techProducts, styleProducts, wellnessProducts, homeProducts, sportProducts, users);
        loyaltySeeder.seed(users, companies);
        addressSeeder.seed(users);
        savedListSeeder.seed(users, techProducts, styleProducts, wellnessProducts);
        var bundles = bundleSeeder.seed(companies, techProducts, styleProducts, wellnessProducts, homeProducts, sportProducts);
        pricingSeeder.seed(companies, techProducts, styleProducts, wellnessProducts, homeProducts, sportProducts, bundles);

        log.info("[DevDataSeeder] Seeded {} users, {} companies, {} products",
                userRepository.count(), companyRepository.count(), productRepository.count());
    }
}
