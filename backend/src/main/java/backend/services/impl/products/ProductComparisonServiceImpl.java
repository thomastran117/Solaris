package backend.services.impl.products;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.core.type.TypeReference;

import backend.dtos.responses.product.ComparedProduct;
import backend.dtos.responses.product.ComparisonRow;
import backend.dtos.responses.product.ProductComparisonResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Product;
import backend.models.core.ProductAttribute;
import backend.models.core.ProductImage;
import backend.models.enums.ProductStatus;
import backend.repositories.ProductRepository;
import backend.repositories.ProductReviewRepository;
import backend.services.impl.SingleFlightCache;
import backend.services.intf.products.ProductComparisonService;

@Service
public class ProductComparisonServiceImpl implements ProductComparisonService {

    private static final Logger log = LoggerFactory.getLogger(ProductComparisonServiceImpl.class);

    private static final int MIN_PRODUCTS = 2;
    private static final int MAX_PRODUCTS = 4;
    /** Comparison results are short-lived; per docs/features/07 the matrix is cached for 2 minutes. */
    private static final long CACHE_TTL_SECONDS = 120;

    private final ProductRepository productRepository;
    private final ProductReviewRepository productReviewRepository;
    private final SingleFlightCache singleFlightCache;
    private final TransactionTemplate txTemplate;
    private final long ttlSeconds;

    public ProductComparisonServiceImpl(
            ProductRepository productRepository,
            ProductReviewRepository productReviewRepository,
            SingleFlightCache singleFlightCache,
            PlatformTransactionManager transactionManager,
            @Value("${app.product.compare-cache-ttl-seconds:120}") long ttlSeconds) {
        this.productRepository = productRepository;
        this.productReviewRepository = productReviewRepository;
        this.singleFlightCache = singleFlightCache;
        // The cache may run the loader on a background early-refresh thread that has no ambient
        // transaction; a read-only TransactionTemplate gives the loader its own session so lazy
        // collections (attributes, images) load on any thread without LazyInitializationException.
        this.txTemplate = new TransactionTemplate(transactionManager);
        this.txTemplate.setReadOnly(true);
        this.ttlSeconds = ttlSeconds > 0 ? ttlSeconds : CACHE_TTL_SECONDS;
    }

    @Override
    public ProductComparisonResponse compare(UUID marketplaceId, List<UUID> productIds) {
        if (productIds == null || productIds.size() < MIN_PRODUCTS || productIds.size() > MAX_PRODUCTS) {
            throw new BadRequestException("Comparison requires between 2 and 4 product IDs");
        }
        // Canonical order: dedupe + sort by id so the response column order, the cache key, and any
        // background early-refresh reload are all deterministic regardless of the order the ids
        // arrive in — two requests for the same set always share one cache entry and one column order.
        List<UUID> requestedIds = productIds.stream().distinct().sorted().toList();
        if (requestedIds.size() < MIN_PRODUCTS) {
            throw new BadRequestException("Comparison requires at least 2 distinct product IDs");
        }

        String idsKey = requestedIds.stream().map(String::valueOf).collect(Collectors.joining(":"));
        String cacheKey = "marketplace:compare:" + marketplaceId + ":" + idsKey;

        return singleFlightCache.getOrLoad(cacheKey, ttlSeconds,
                () -> txTemplate.execute(status -> buildComparison(marketplaceId, requestedIds)),
                new TypeReference<ProductComparisonResponse>() {});
    }

    private ProductComparisonResponse buildComparison(UUID marketplaceId, List<UUID> requestedIds) {
        // Public, unauthenticated endpoint — only compare ACTIVE + marketplaceListed products,
        // matching the catalog search/detail endpoints. A requested id that isn't a publicly
        // listed product in this marketplace yields a 404 rather than silently dropping a column.
        Map<UUID, Product> byId = productRepository.findAllByIdInAndMarketplaceIdWithAttributes(requestedIds, marketplaceId).stream()
                .filter(p -> p.getStatus() == ProductStatus.ACTIVE && p.isMarketplaceListed())
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));

        List<Product> products = requestedIds.stream()
                .map(id -> {
                    Product p = byId.get(id);
                    if (p == null) {
                        throw new ResourceNotFoundException("Product " + id + " is not available in this marketplace");
                    }
                    return p;
                })
                .toList();

        Map<UUID, double[]> ratingMap = buildRatingMap(requestedIds);

        List<ComparedProduct> columns = products.stream()
                .map(p -> toComparedProduct(p, ratingMap.get(p.getId())))
                .toList();

        List<ComparisonRow> rows = buildAttributeRows(products);

        return new ProductComparisonResponse(columns, rows);
    }

    private ComparedProduct toComparedProduct(Product p, double[] stats) {
        Double avgRating = (stats != null && stats[1] > 0) ? stats[0] : null;
        long reviewCount = stats != null ? (long) stats[1] : 0L;
        return new ComparedProduct(
                p.getId(),
                p.getName(),
                p.getPrice(),
                p.getCurrency(),
                avgRating,
                reviewCount,
                stockStatus(p),
                resolveImageUrl(p));
    }

    /**
     * Builds one row per distinct attribute name across all compared products, ordered by the
     * attribute's display order (then name) so the matrix reads in a stable, merchant-defined
     * sequence. Each row maps every product's id to its value for that attribute, or {@code null}
     * when the product lacks it — keeping every row aligned to the same set of columns.
     */
    private List<ComparisonRow> buildAttributeRows(List<Product> products) {
        // Preserve first-seen order while tracking the lowest display order per attribute name.
        Map<String, Integer> orderByName = new LinkedHashMap<>();
        for (Product p : products) {
            for (ProductAttribute a : p.getAttributes()) {
                orderByName.merge(a.getName(), a.getDisplayOrder(), Math::min);
            }
        }

        return orderByName.entrySet().stream()
                .sorted(Comparator
                        .comparingInt(Map.Entry<String, Integer>::getValue)
                        .thenComparing(Map.Entry::getKey))
                .map(entry -> {
                    String name = entry.getKey();
                    Map<UUID, String> valuesByProductId = new LinkedHashMap<>();
                    for (Product p : products) {
                        String value = p.getAttributes().stream()
                                .filter(a -> a.getName().equals(name))
                                .map(ProductAttribute::getValue)
                                .findFirst()
                                .orElse(null);
                        valuesByProductId.put(p.getId(), value);
                    }
                    return new ComparisonRow(name, valuesByProductId);
                })
                .toList();
    }

    /**
     * Coarse availability label for the comparison column. A null stock means inventory is not
     * tracked (treated as in stock); otherwise it reflects out-of-stock and low-stock thresholds.
     */
    private String stockStatus(Product p) {
        Integer stock = p.getStock();
        if (stock != null && stock <= 0) {
            return "OUT_OF_STOCK";
        }
        Integer threshold = p.getLowStockThreshold();
        if (stock != null && threshold != null && stock <= threshold) {
            return "LOW_STOCK";
        }
        return "IN_STOCK";
    }

    private String resolveImageUrl(Product p) {
        if (p.getThumbnailUrl() != null && !p.getThumbnailUrl().isBlank()) {
            return p.getThumbnailUrl();
        }
        return p.getImages().stream()
                .min(Comparator.comparingInt(ProductImage::getDisplayOrder))
                .map(ProductImage::getImageUrl)
                .orElse(null);
    }

    private Map<UUID, double[]> buildRatingMap(List<UUID> productIds) {
        Map<UUID, double[]> map = new HashMap<>();
        try {
            List<Object[]> rows = productReviewRepository.findAverageRatingsByProductIds(productIds);
            for (Object[] row : rows) {
                UUID productId = (UUID) row[0];
                double avg = ((Number) row[1]).doubleValue();
                double count = ((Number) row[2]).doubleValue();
                map.put(productId, new double[]{avg, count});
            }
        } catch (Exception e) {
            log.warn("[COMPARE] Failed to load ratings", e);
        }
        return map;
    }
}
