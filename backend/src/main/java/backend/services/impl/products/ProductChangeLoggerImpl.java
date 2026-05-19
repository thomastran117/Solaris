package backend.services.impl.products;

import backend.models.core.Product;
import backend.models.core.ProductChangeLog;
import backend.models.core.ProductVariant;
import backend.models.core.User;
import backend.models.enums.ChangeSource;
import backend.models.enums.ProductChangeType;
import backend.repositories.ProductChangeLogRepository;
import backend.repositories.UserRepository;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import backend.services.intf.products.ProductChangeLogger;

@Service
public class ProductChangeLoggerImpl implements ProductChangeLogger {

    private final ProductChangeLogRepository changeLogRepository;
    private final UserRepository userRepository;
    private final AuditorAware<UUID> auditorAware;

    public ProductChangeLoggerImpl(ProductChangeLogRepository changeLogRepository,
                                   UserRepository userRepository,
                                   AuditorAware<UUID> auditorAware) {
        this.changeLogRepository = changeLogRepository;
        this.userRepository = userRepository;
        this.auditorAware = auditorAware;
    }

    // --- Product ---

    @Override
    public void logCreate(Product saved, ChangeSource source) {
        List<ProductChangeLog> rows = new ArrayList<>();
        User actor = currentActor();
        for (ProductField f : ProductField.values()) {
            String v = f.read(saved);
            if (v == null) continue;
            rows.add(row(saved, null, f.name(), null, v, ProductChangeType.CREATED, source, actor, null));
        }
        if (!rows.isEmpty()) changeLogRepository.saveAll(rows);
    }

    @Override
    public void logUpdate(Product before, Product after, ChangeSource source, UUID revertedFromLogId) {
        List<ProductChangeLog> rows = new ArrayList<>();
        User actor = currentActor();
        for (ProductField f : ProductField.values()) {
            String oldV = f.read(before);
            String newV = f.read(after);
            if (Objects.equals(oldV, newV)) continue;
            rows.add(row(after, null, f.name(), oldV, newV, ProductChangeType.UPDATED, source, actor, revertedFromLogId));
        }
        if (!rows.isEmpty()) changeLogRepository.saveAll(rows);
    }

    @Override
    public void logDelete(Product before) {
        ProductChangeLog row = row(before, null, "__entity__", "EXISTS", "DELETED",
                ProductChangeType.DELETED, ChangeSource.USER, currentActor(), null);
        changeLogRepository.save(row);
    }

    // --- Variant ---

    @Override
    public void logVariantCreate(ProductVariant saved, ChangeSource source) {
        List<ProductChangeLog> rows = new ArrayList<>();
        User actor = currentActor();
        for (VariantField f : VariantField.values()) {
            String v = f.read(saved);
            if (v == null) continue;
            rows.add(row(saved.getProduct(), saved, f.name(), null, v, ProductChangeType.CREATED, source, actor, null));
        }
        if (!rows.isEmpty()) changeLogRepository.saveAll(rows);
    }

    @Override
    public void logVariantUpdate(ProductVariant before, ProductVariant after, ChangeSource source, UUID revertedFromLogId) {
        List<ProductChangeLog> rows = new ArrayList<>();
        User actor = currentActor();
        for (VariantField f : VariantField.values()) {
            String oldV = f.read(before);
            String newV = f.read(after);
            if (Objects.equals(oldV, newV)) continue;
            rows.add(row(after.getProduct(), after, f.name(), oldV, newV, ProductChangeType.UPDATED, source, actor, revertedFromLogId));
        }
        if (!rows.isEmpty()) changeLogRepository.saveAll(rows);
    }

    @Override
    public void logVariantDelete(ProductVariant before) {
        ProductChangeLog row = row(before.getProduct(), before, "__entity__", "EXISTS", "DELETED",
                ProductChangeType.DELETED, ChangeSource.USER, currentActor(), null);
        changeLogRepository.save(row);
    }

    // --- Snapshots ---

    @Override
    public Product snapshot(Product s) {
        Product c = new Product();
        c.setId(s.getId());
        c.setCompany(s.getCompany());
        c.setMarketplaceId(s.getMarketplaceId());
        c.setMarketplaceListed(s.isMarketplaceListed());
        c.setName(s.getName());
        c.setDescription(s.getDescription());
        c.setSku(s.getSku());
        c.setPrice(s.getPrice());
        c.setCompareAtPrice(s.getCompareAtPrice());
        c.setCurrency(s.getCurrency());
        c.setCategory(s.getCategory());
        c.setBrand(s.getBrand());
        c.setTags(s.getTags());
        c.setThumbnailUrl(s.getThumbnailUrl());
        c.setWeight(s.getWeight());
        c.setWeightUnit(s.getWeightUnit());
        c.setStatus(s.getStatus());
        c.setScheduledPublishAt(s.getScheduledPublishAt());
        c.setFeatured(s.isFeatured());
        c.setPurchasable(s.isPurchasable());
        c.setListed(s.isListed());
        c.setBackorderEnabled(s.isBackorderEnabled());
        c.setSubscribable(s.isSubscribable());
        c.setSubscriptionIntervals(s.getSubscriptionIntervals());
        c.setSubscriptionDiscountPercent(s.getSubscriptionDiscountPercent());
        c.setBoostWeight(s.getBoostWeight());
        c.setPinnedUntil(s.getPinnedUntil());
        c.setPinnedRank(s.getPinnedRank());
        c.setLowStockThreshold(s.getLowStockThreshold());
        c.setLowStockThresholdPercent(s.getLowStockThresholdPercent());
        c.setMaxStock(s.getMaxStock());
        c.setAutoRestockEnabled(s.isAutoRestockEnabled());
        c.setAutoRestockQty(s.getAutoRestockQty());
        return c;
    }

    @Override
    public ProductVariant snapshot(ProductVariant s) {
        ProductVariant c = new ProductVariant();
        c.setId(s.getId());
        c.setProduct(s.getProduct());
        c.setSku(s.getSku());
        c.setPrice(s.getPrice());
        c.setCompareAtPrice(s.getCompareAtPrice());
        c.setLowStockThreshold(s.getLowStockThreshold());
        c.setLowStockThresholdPercent(s.getLowStockThresholdPercent());
        c.setMaxStock(s.getMaxStock());
        c.setAutoRestockEnabled(s.isAutoRestockEnabled());
        c.setAutoRestockQty(s.getAutoRestockQty());
        c.setPurchasable(s.isPurchasable());
        c.setBackorderEnabled(s.isBackorderEnabled());
        c.setOption1(s.getOption1());
        c.setOption2(s.getOption2());
        c.setOption3(s.getOption3());
        c.setDisplayOrder(s.getDisplayOrder());
        return c;
    }

    // --- Helpers ---

    private ProductChangeLog row(Product product,
                                 ProductVariant variant,
                                 String fieldName,
                                 String oldValue,
                                 String newValue,
                                 ProductChangeType type,
                                 ChangeSource source,
                                 User actor,
                                 UUID revertedFromLogId) {
        ProductChangeLog log = new ProductChangeLog();
        log.setProduct(product);
        log.setVariant(variant);
        log.setCompanyId(product.getCompany().getId());
        log.setFieldName(fieldName);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        log.setChangeType(type);
        log.setSource(source);
        log.setChangedBy(actor);
        log.setRevertedFromLogId(revertedFromLogId);
        return log;
    }

    private User currentActor() {
        return auditorAware.getCurrentAuditor()
                .map(userRepository::getReferenceById)
                .orElse(null);
    }

    private static String str(Object o) {
        if (o == null) return null;
        if (o instanceof Instant i) return i.toString();
        return o.toString();
    }

    /**
     * Tracked top-level Product fields. {@code stock} is intentionally excluded — see
     * {@code InventoryAdjustment} for stock history.
     */
    private enum ProductField {
        name(p -> str(p.getName())),
        description(p -> str(p.getDescription())),
        sku(p -> str(p.getSku())),
        price(p -> str(p.getPrice())),
        compareAtPrice(p -> str(p.getCompareAtPrice())),
        currency(p -> str(p.getCurrency())),
        category(p -> str(p.getCategory())),
        brand(p -> str(p.getBrand())),
        tags(p -> str(p.getTags())),
        thumbnailUrl(p -> str(p.getThumbnailUrl())),
        weight(p -> str(p.getWeight())),
        weightUnit(p -> str(p.getWeightUnit())),
        status(p -> str(p.getStatus())),
        scheduledPublishAt(p -> str(p.getScheduledPublishAt())),
        featured(p -> Boolean.toString(p.isFeatured())),
        purchasable(p -> Boolean.toString(p.isPurchasable())),
        listed(p -> Boolean.toString(p.isListed())),
        backorderEnabled(p -> Boolean.toString(p.isBackorderEnabled())),
        preorderEnabled(p -> Boolean.toString(p.isPreorderEnabled())),
        preorderExpectedDate(p -> str(p.getPreorderExpectedDate())),
        subscribable(p -> Boolean.toString(p.isSubscribable())),
        subscriptionIntervals(p -> str(p.getSubscriptionIntervals())),
        subscriptionDiscountPercent(p -> str(p.getSubscriptionDiscountPercent())),
        boostWeight(p -> str(p.getBoostWeight())),
        pinnedUntil(p -> str(p.getPinnedUntil())),
        pinnedRank(p -> str(p.getPinnedRank())),
        lowStockThreshold(p -> str(p.getLowStockThreshold())),
        lowStockThresholdPercent(p -> str(p.getLowStockThresholdPercent())),
        maxStock(p -> str(p.getMaxStock())),
        autoRestockEnabled(p -> Boolean.toString(p.isAutoRestockEnabled())),
        autoRestockQty(p -> str(p.getAutoRestockQty())),
        marketplaceListed(p -> Boolean.toString(p.isMarketplaceListed()));

        private final Function<Product, String> reader;
        ProductField(Function<Product, String> reader) { this.reader = reader; }
        String read(Product p) { return reader.apply(p); }
    }

    /**
     * Tracked variant fields. {@code stock} is intentionally excluded.
     */
    private enum VariantField {
        sku(v -> str(v.getSku())),
        price(v -> str(v.getPrice())),
        compareAtPrice(v -> str(v.getCompareAtPrice())),
        lowStockThreshold(v -> str(v.getLowStockThreshold())),
        lowStockThresholdPercent(v -> str(v.getLowStockThresholdPercent())),
        maxStock(v -> str(v.getMaxStock())),
        autoRestockEnabled(v -> Boolean.toString(v.isAutoRestockEnabled())),
        autoRestockQty(v -> str(v.getAutoRestockQty())),
        purchasable(v -> Boolean.toString(v.isPurchasable())),
        backorderEnabled(v -> Boolean.toString(v.isBackorderEnabled())),
        preorderEnabled(v -> Boolean.toString(v.isPreorderEnabled())),
        preorderExpectedDate(v -> str(v.getPreorderExpectedDate())),
        option1(v -> str(v.getOption1())),
        option2(v -> str(v.getOption2())),
        option3(v -> str(v.getOption3())),
        displayOrder(v -> Integer.toString(v.getDisplayOrder()));

        private final Function<ProductVariant, String> reader;
        VariantField(Function<ProductVariant, String> reader) { this.reader = reader; }
        String read(ProductVariant v) { return reader.apply(v); }
    }
}
