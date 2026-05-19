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
import backend.repositories.OrderRepository;

@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;

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

    // New seeders
    private final SmallCompaniesSeeder smallCompaniesSeeder;
    private final UserProfileSeeder userProfileSeeder;
    private final CompanyReturnLocationSeeder companyReturnLocationSeeder;
    private final MarketplaceSeeder marketplaceSeeder;
    private final CollectionSeeder collectionSeeder;
    private final LocationStockSeeder locationStockSeeder;
    private final OrderSeeder orderSeeder;
    private final SubscriptionSeeder subscriptionSeeder;
    private final LoyaltyTransactionSeeder loyaltyTransactionSeeder;
    private final SupportTicketSeeder supportTicketSeeder;
    private final StockNotificationSeeder stockNotificationSeeder;
    private final ReviewEnrichmentSeeder reviewEnrichmentSeeder;

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

        userProfileSeeder.seed(users);
        companyReturnLocationSeeder.seed(companies);
        marketplaceSeeder.seed(companies);
        collectionSeeder.seed(companies, techProducts, styleProducts, wellnessProducts, homeProducts, sportProducts);
        locationStockSeeder.seed(companies, users, techProducts, styleProducts, wellnessProducts, homeProducts, sportProducts);

        var orders = orderSeeder.seed(users, techProducts, styleProducts, wellnessProducts, homeProducts, sportProducts);
        subscriptionSeeder.seed(users, companies, wellnessProducts);
        loyaltyTransactionSeeder.seed(users, companies, orders);
        supportTicketSeeder.seed(users, orders);
        stockNotificationSeeder.seed(users, techProducts, styleProducts, wellnessProducts);
        reviewEnrichmentSeeder.seed(users, orders, techProducts, wellnessProducts);
        smallCompaniesSeeder.seed(users);

        log.info("[DevDataSeeder] Seeded {} users, {} companies, {} products, {} orders",
                userRepository.count(), companyRepository.count(),
                productRepository.count(), orderRepository.count());
    }
}
