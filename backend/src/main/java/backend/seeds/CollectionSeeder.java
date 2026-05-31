package backend.seeds;

import backend.models.core.Collection;
import backend.models.core.CollectionProduct;
import backend.models.core.Company;
import backend.models.core.Product;
import backend.models.enums.CollectionMembershipSource;
import backend.models.enums.CollectionStatus;
import backend.models.enums.CollectionType;
import backend.repositories.CollectionProductRepository;
import backend.repositories.CollectionRepository;
import backend.seeds.CompanySeeder.SeededCompanies;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class CollectionSeeder {

    private final CollectionRepository collectionRepository;
    private final CollectionProductRepository collectionProductRepository;

    public void seed(SeededCompanies companies,
                     List<Product> tech, List<Product> style,
                     List<Product> wellness, List<Product> home, List<Product> sport) {

        // TechGadgets
        addToCollection(collection(companies.tech(), "best-sellers", "Best Sellers",
                "Our most popular electronics and gadgets.", true, 1),
                tech, "Headphones", "Smart Watch", "Wireless Charging", "Mechanical Keyboard", "Gaming Mouse", "Bluetooth Speaker");

        addToCollection(collection(companies.tech(), "smart-home", "Smart Home",
                "Transform your living space with smart technology.", false, null),
                tech, "Smart Home Hub", "Smart Thermostat", "Smart LED", "Mesh WiFi", "Ring Light");

        addToCollection(collection(companies.tech(), "creator-gear", "Creator Gear",
                "Professional tools for content creators and streamers.", false, null),
                tech, "Webcam", "Mic Arm", "Ring Light", "Green Screen", "Capture Card", "Laptop Stand");

        // StyleHub
        addToCollection(collection(companies.style(), "new-arrivals", "New Arrivals",
                "Fresh styles just landed — be the first to wear them.", true, 1),
                style, "Satin Bomber", "Chelsea Boots", "Linen Button", "Knit Beanie", "Baseball Cap");

        addToCollection(collection(companies.style(), "activewear", "Activewear",
                "Performance-ready clothing for every workout.", false, null),
                style, "Running Shorts", "Leggings", "Sports Bra", "Hoodie", "Sweatshirt");

        addToCollection(collection(companies.style(), "outerwear", "Outerwear",
                "Jackets and coats for every season.", false, null),
                style, "Coat", "Bomber", "Chelsea", "Hoodie");

        // WellnessWorld
        addToCollection(collection(companies.wellness(), "top-supplements", "Top Supplements",
                "Science-backed supplements to fuel your goals.", true, 1),
                wellness, "Whey Protein", "Creatine", "Pre-Workout", "Collagen", "Magnesium");

        addToCollection(collection(companies.wellness(), "skincare", "Skincare",
                "Brightening and hydrating skincare essentials.", false, null),
                wellness, "Serum", "Hyaluronic", "Retinol", "Face Mask", "Moisturiser");

        addToCollection(collection(companies.wellness(), "sleep-recovery", "Sleep & Recovery",
                "Wind down, recover, and wake up refreshed.", false, null),
                wellness, "Sleep", "Herbal Tea", "Magnesium", "Collagen");

        // HomeNest
        addToCollection(collection(companies.home(), "smart-home", "Smart Home",
                "Connected devices for a smarter home.", true, 1),
                home, "Smart LED", "Smart Thermostat", "Smart Video Doorbell");

        addToCollection(collection(companies.home(), "kitchen", "Kitchen",
                "Elevate your kitchen with premium tools.", false, null),
                home, "French Press", "Chef's Knife", "Pour-Over", "Spice Rack", "Cutting Board");

        addToCollection(collection(companies.home(), "bedroom", "Bedroom",
                "Create the bedroom of your dreams.", false, null),
                home, "Duvet", "Sheet", "Weighted Blanket", "Knit Throw", "Floor Lamp");

        // SportZone
        addToCollection(collection(companies.sport(), "running", "Running",
                "Everything you need to hit the road or trail.", true, 1),
                sport, "Running Shorts", "Running Watch", "GPS", "Athletic Socks", "Water Bottle");

        addToCollection(collection(companies.sport(), "training", "Training",
                "Build strength and endurance with pro-grade gear.", false, null),
                sport, "Resistance Band", "Dumbbell", "Leggings", "Sports Bra", "Gym Duffle");

        addToCollection(collection(companies.sport(), "nutrition", "Nutrition",
                "Fuel your training with clean, effective nutrition.", false, null),
                sport, "Whey Isolate", "Creatine", "Protein Bar", "Pre-Workout");
    }

    private Collection collection(Company company, String slug, String name,
                                   String description, boolean featured, Integer featuredRank) {
        return collectionRepository.findBySlugAndCompanyId(slug, company.getId()).orElseGet(() -> {
            Collection c = new Collection();
            c.setCompany(company);
            c.setSlug(slug);
            c.setName(name);
            c.setDescription(description);
            c.setType(CollectionType.STATIC);
            c.setStatus(CollectionStatus.ACTIVE);
            c.setFeatured(featured);
            c.setFeaturedRank(featuredRank);
            return collectionRepository.save(c);
        });
    }

    private void addToCollection(Collection collection, List<Product> products, String... nameFragments) {
        for (String fragment : nameFragments) {
            products.stream()
                    .filter(p -> p.getName().contains(fragment))
                    .findFirst()
                    .ifPresent(p -> {
                        if (!collectionProductRepository.existsByCollectionIdAndProductId(collection.getId(), p.getId())) {
                            CollectionProduct cp = new CollectionProduct();
                            cp.setCollection(collection);
                            cp.setProduct(p);
                            cp.setSource(CollectionMembershipSource.MANUAL);
                            collectionProductRepository.save(cp);
                        }
                    });
        }
    }
}
