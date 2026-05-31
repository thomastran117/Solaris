package backend.seeds;

import backend.models.core.Company;
import backend.models.core.InventoryAdjustment;
import backend.models.core.InventoryLocation;
import backend.models.core.LocationStock;
import backend.models.core.Product;
import backend.models.core.RestockRequest;
import backend.models.core.User;
import backend.models.enums.AdjustmentReason;
import backend.models.enums.RestockStatus;
import backend.repositories.InventoryAdjustmentRepository;
import backend.repositories.InventoryLocationRepository;
import backend.repositories.LocationStockRepository;
import backend.repositories.RestockRequestRepository;
import backend.seeds.CompanySeeder.SeededCompanies;
import backend.seeds.UserSeeder.SeededUsers;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class LocationStockSeeder {

    private final InventoryLocationRepository locationRepository;
    private final LocationStockRepository locationStockRepository;
    private final InventoryAdjustmentRepository adjustmentRepository;
    private final RestockRequestRepository restockRequestRepository;

    public void seed(SeededCompanies companies, SeededUsers users,
                     List<Product> tech, List<Product> style,
                     List<Product> wellness, List<Product> home, List<Product> sport) {

        seedCompanyStock(companies.tech(),     tech);
        seedCompanyStock(companies.style(),    style);
        seedCompanyStock(companies.wellness(), wellness);
        seedCompanyStock(companies.home(),     home);
        seedCompanyStock(companies.sport(),    sport);

        seedAdjustments(companies, users, tech, home, sport);
        seedRestockRequests(companies, users, wellness, style);
    }

    private void seedCompanyStock(Company company, List<Product> products) {
        List<InventoryLocation> locations = locationRepository.findAllByCompanyIdOrderByDisplayOrderAscNameAsc(company.getId());
        if (locations.size() < 2) return;

        InventoryLocation primary   = locations.get(0);
        InventoryLocation secondary = locations.get(1);

        for (Product product : products) {
            int total = product.getStock();
            int primaryStock   = (int) Math.round(total * 0.6);
            int secondaryStock = total - primaryStock;

            locationStock(primary,   product, primaryStock);
            locationStock(secondary, product, secondaryStock);
        }
    }

    private void locationStock(InventoryLocation location, Product product, int stock) {
        if (locationStockRepository.findByLocationIdAndProductIdAndVariantRef(
                location.getId(), product.getId(), null).isPresent()) return;
        LocationStock ls = new LocationStock();
        ls.setLocation(location);
        ls.setProduct(product);
        ls.setStock(Math.max(0, stock));
        ls.setLowStockThreshold(product.getLowStockThreshold());
        locationStockRepository.save(ls);
    }

    private void seedAdjustments(SeededCompanies companies, SeededUsers users,
                                  List<Product> tech, List<Product> home, List<Product> sport) {
        // Manual restock for a TechGadgets product
        tech.stream().filter(p -> p.getName().contains("Wireless Noise")).findFirst().ifPresent(p -> {
            if (adjustmentRepository.findAll().stream().noneMatch(a -> a.getProduct().getId().equals(p.getId()))) {
                adjustment(p, users.admin(), 50, p.getStock(), p.getStock() + 50, AdjustmentReason.RESTOCK, "Quarterly restock batch");
            }
        });

        // Damage write-off for a HomeNest product
        home.stream().filter(p -> p.getName().contains("Artisan Soy")).findFirst().ifPresent(p -> {
            if (adjustmentRepository.findAll().stream().noneMatch(a -> a.getProduct().getId().equals(p.getId()))) {
                int newStock = Math.max(0, p.getStock() - 5);
                adjustment(p, users.admin(), -5, p.getStock(), newStock, AdjustmentReason.DAMAGE, "Warehouse transit damage — 5 units written off");
            }
        });

        // Sale reconciliation for a SportZone product
        sport.stream().filter(p -> p.getName().contains("Creatine")).findFirst().ifPresent(p -> {
            if (adjustmentRepository.findAll().stream().noneMatch(a -> a.getProduct().getId().equals(p.getId()))) {
                int newStock = Math.max(0, p.getStock() - 10);
                adjustment(p, users.admin(), -10, p.getStock(), newStock, AdjustmentReason.COUNT_CORRECTION, "Physical count reconciliation after flash sale");
            }
        });
    }

    private void adjustment(Product product, User adjustedBy, int delta,
                             int previousStock, int newStock,
                             AdjustmentReason reason, String note) {
        InventoryAdjustment adj = new InventoryAdjustment();
        adj.setProduct(product);
        adj.setAdjustedBy(adjustedBy);
        adj.setDelta(delta);
        adj.setPreviousStock(previousStock);
        adj.setNewStock(newStock);
        adj.setReason(reason);
        adj.setNote(note);
        adjustmentRepository.save(adj);
    }

    private void seedRestockRequests(SeededCompanies companies, SeededUsers users,
                                      List<Product> wellness, List<Product> style) {
        // Pending restock for a WellnessWorld product
        wellness.stream().filter(p -> p.getName().contains("Whey Protein")).findFirst().ifPresent(p -> {
            if (restockRequestRepository.findAll().stream().noneMatch(r -> r.getProduct().getId().equals(p.getId()))) {
                RestockRequest rr = new RestockRequest();
                rr.setCompany(companies.wellness());
                rr.setProduct(p);
                rr.setRequestedQty(100);
                rr.setExpectedArrivalDate(LocalDate.now().plusDays(14));
                rr.setStatus(RestockStatus.PENDING);
                rr.setSupplierNote("Priority reorder — low stock alert triggered");
                rr.setCreatedBy(users.wellnessMerchant());
                restockRequestRepository.save(rr);
            }
        });

        // Received restock for a StyleHub product
        style.stream().filter(p -> p.getName().contains("Slim-Fit")).findFirst().ifPresent(p -> {
            if (restockRequestRepository.findAll().stream().noneMatch(r -> r.getProduct().getId().equals(p.getId()))) {
                RestockRequest rr = new RestockRequest();
                rr.setCompany(companies.style());
                rr.setProduct(p);
                rr.setRequestedQty(50);
                rr.setReceivedQty(50);
                rr.setExpectedArrivalDate(LocalDate.now().minusDays(5));
                rr.setReceivedAt(Instant.now().minusSeconds(60 * 60 * 24 * 3));
                rr.setStatus(RestockStatus.RECEIVED);
                rr.setSupplierNote("Full order received in good condition");
                rr.setCreatedBy(users.styleMerchant());
                restockRequestRepository.save(rr);
            }
        });
    }
}
