package backend.seeds;

import backend.models.core.Product;
import backend.models.core.ProductRelationship;
import backend.models.enums.ProductRelationshipType;
import backend.repositories.ProductRelationshipRepository;
import backend.repositories.ProductRepository;
import backend.seeds.CompanySeeder.SeededCompanies;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class ProductRelationshipSeeder {

    private final ProductRepository productRepository;
    private final ProductRelationshipRepository productRelationshipRepository;

    public void seed(SeededCompanies companies) {
        seedTech(companies);
        seedWellness(companies);
        seedHome(companies);
        log.info("[ProductRelationshipSeeder] Product relationships seeded");
    }

    private void seedTech(SeededCompanies companies) {
        var co = companies.tech();
        // Bluetooth Speaker → Wireless Headphones (UPGRADE: headphones are premium audio step-up)
        link(co, "TECH-PBS-001", "TECH-WNC-HDR-001", ProductRelationshipType.UPGRADE, null);
        // Smart Plug → Smart Power Strip (UPGRADE: strip expands what the plug does)
        link(co, "TECH-SPL-001", "TECH-SPS-001", ProductRelationshipType.UPGRADE, null);
        // USB-C Hub → Portable Monitor (ACCESSORY: hub is a companion peripheral)
        link(co, "TECH-HUB-001", "TECH-PMN-001", ProductRelationshipType.ACCESSORY, "Expands connectivity for the monitor");
        // USB-C Hub → Mini PC (ACCESSORY: hub expands connectivity for mini PC)
        link(co, "TECH-HUB-001", "TECH-MPC-001", ProductRelationshipType.ACCESSORY, "Expands ports on the mini PC");
        // Smart LED Strip → Smart Home Hub (ACCESSORY: strip integrates into hub ecosystem)
        link(co, "TECH-LED-001", "TECH-SHH-001", ProductRelationshipType.ACCESSORY, "Integrates into the smart home hub ecosystem");
        // Wireless Headphones → Bluetooth Speaker (ALTERNATIVE: both personal audio, different use cases)
        link(co, "TECH-WNC-HDR-001", "TECH-PBS-001", ProductRelationshipType.ALTERNATIVE, null);
        // Smart Watch → VR Headset (ALTERNATIVE: both are immersive wearables)
        link(co, "TECH-SWX-001", "TECH-VRS-001", ProductRelationshipType.ALTERNATIVE, null);
        // Smart Watch → Wireless Headphones (SIMILAR: both premium personal-tech wearables)
        link(co, "TECH-SWX-001", "TECH-WNC-HDR-001", ProductRelationshipType.SIMILAR, null);
        // Mechanical Keyboard → Webcam (SIMILAR: both desk productivity peripherals)
        link(co, "TECH-MKB-001", "TECH-WEB-001", ProductRelationshipType.SIMILAR, null);
    }

    private void seedWellness(SeededCompanies companies) {
        var co = companies.wellness();
        // Resistance Bands → Yoga Mat (ACCESSORY: bands are commonly paired with mat workouts)
        link(co, "WELL-RBS-001", "WELL-YGM-001", ProductRelationshipType.ACCESSORY, "Pairs great with mat workouts");
        // Whey Protein → Collagen Peptides (ALTERNATIVE: both are daily protein supplements)
        link(co, "WELL-WPP-001", "WELL-COL-001", ProductRelationshipType.ALTERNATIVE, null);
        // Essential Oil Diffuser → Soy Wax Candles (ALTERNATIVE: both deliver ambient scent)
        link(co, "WELL-EOD-001", "WELL-SWC-001", ProductRelationshipType.ALTERNATIVE, null);
        // Yoga Mat → Resistance Bands (SIMILAR: both bodyweight fitness gear)
        link(co, "WELL-YGM-001", "WELL-RBS-001", ProductRelationshipType.SIMILAR, null);
    }

    private void seedHome(SeededCompanies companies) {
        var co = companies.home();
        // Smart Plug Mini → Smart Power Strip (UPGRADE: strip is the expanded smart-plug solution)
        link(co, "HOME-SPL-001", "HOME-SPS-001", ProductRelationshipType.UPGRADE, null);
        // Indoor Camera → Video Doorbell (REPLACEMENT: doorbell replaces indoor cam for entry monitoring)
        link(co, "HOME-CAM-001", "HOME-SVD-001", ProductRelationshipType.REPLACEMENT, "Better solution for entry monitoring");
        // Weighted Blanket → Organic Duvet Cover (ACCESSORY: blanket layers over the duvet system)
        link(co, "HOME-WBL-001", "HOME-DVC-001", ProductRelationshipType.ACCESSORY, "Layers over the duvet for added warmth");
        // Indoor Camera → Water Leak Sensor (SIMILAR: both smart home sensors)
        link(co, "HOME-CAM-001", "HOME-WLS-001", ProductRelationshipType.SIMILAR, null);
    }

    private void link(backend.models.core.Company company, String sourceSku, String targetSku,
                      ProductRelationshipType type, String note) {
        Optional<Product> sourceOpt = productRepository.findBySkuAndCompanyId(sourceSku, company.getId());
        Optional<Product> targetOpt = productRepository.findBySkuAndCompanyId(targetSku, company.getId());

        if (sourceOpt.isEmpty() || targetOpt.isEmpty()) {
            log.warn("[ProductRelationshipSeeder] Skipping {}->{}: product not found", sourceSku, targetSku);
            return;
        }

        Product source = sourceOpt.get();
        Product target = targetOpt.get();

        if (productRelationshipRepository.existsBySourceProductIdAndTargetProductIdAndType(
                source.getId(), target.getId(), type)) {
            return;
        }

        ProductRelationship rel = new ProductRelationship();
        rel.setSourceProduct(source);
        rel.setTargetProduct(target);
        rel.setType(type);
        rel.setNote(note);
        productRelationshipRepository.save(rel);
    }
}
