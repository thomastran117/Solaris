package backend.services.impl.products;

import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.product.ActivePromotionSummary;
import backend.dtos.responses.product.MarketplaceCatalogProductResponse;
import backend.dtos.responses.product.ProductImageResponse;
import backend.dtos.responses.product.ProductVariantResponse;
import backend.models.core.MarketplaceVendor;
import backend.models.core.Product;
import backend.models.core.ProductImage;
import backend.models.core.ProductVariant;
import backend.repositories.MarketplaceVendorRepository;
import backend.repositories.OrderRepository;
import backend.repositories.ProductRepository;
import backend.repositories.SavedListItemRepository;
import backend.services.impl.pricing.ActivePromotionLookupService;
import backend.services.intf.products.ProductFeedService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ProductFeedServiceImpl implements ProductFeedService {

    private static final int MAX_PURCHASED_EXCLUSIONS = 100;
    private static final int MAX_WISHLIST_SIGNALS = 200;
    private static final int MAX_AFFINITY_TERMS = 5;
    private static final String CATEGORY_SENTINEL = "__no_match__";
    private static final UUID ID_SENTINEL = new UUID(0L, 0L);

    private final OrderRepository orderRepository;
    private final SavedListItemRepository savedListItemRepository;
    private final ProductRepository productRepository;
    private final MarketplaceVendorRepository marketplaceVendorRepository;
    private final ActivePromotionLookupService activePromotionLookupService;

    public ProductFeedServiceImpl(
            OrderRepository orderRepository,
            SavedListItemRepository savedListItemRepository,
            ProductRepository productRepository,
            MarketplaceVendorRepository marketplaceVendorRepository,
            ActivePromotionLookupService activePromotionLookupService) {
        this.orderRepository = orderRepository;
        this.savedListItemRepository = savedListItemRepository;
        this.productRepository = productRepository;
        this.marketplaceVendorRepository = marketplaceVendorRepository;
        this.activePromotionLookupService = activePromotionLookupService;
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<MarketplaceCatalogProductResponse> getFeed(
            UUID marketplaceId, UUID userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        // Step 1: Purchased product IDs for exclusion (capped at 100)
        List<UUID> purchasedIds = orderRepository.findPurchasedProductIdsByUserId(
                userId, PageRequest.of(0, MAX_PURCHASED_EXCLUSIONS));
        List<UUID> excludedIds = purchasedIds.isEmpty() ? List.of(ID_SENTINEL) : purchasedIds;

        // Step 2: Category/brand affinity from purchase history (weight ×3)
        Map<String, Long> categoryScores = new LinkedHashMap<>();
        Map<String, Long> brandScores = new LinkedHashMap<>();

        for (Object[] row : orderRepository.findTopPurchasedCategoriesByUserId(userId)) {
            categoryScores.merge((String) row[0], ((Number) row[1]).longValue() * 3L, Long::sum);
        }
        for (Object[] row : orderRepository.findTopPurchasedBrandsByUserId(userId)) {
            brandScores.merge((String) row[0], ((Number) row[1]).longValue() * 3L, Long::sum);
        }

        // Step 3: Category/brand affinity from active wishlist (weight ×1, capped at 200)
        List<UUID> wishlistIds = savedListItemRepository.findActiveWishlistProductIdsByUserId(userId);
        if (!wishlistIds.isEmpty()) {
            List<UUID> cappedWishlistIds = wishlistIds.size() > MAX_WISHLIST_SIGNALS
                    ? wishlistIds.subList(0, MAX_WISHLIST_SIGNALS)
                    : wishlistIds;
            for (Product p : productRepository.findAllById(cappedWishlistIds)) {
                if (p.getCategory() != null) categoryScores.merge(p.getCategory(), 1L, Long::sum);
                if (p.getBrand() != null) brandScores.merge(p.getBrand(), 1L, Long::sum);
            }
        }

        // Step 4: Top-5 categories and brands by score
        List<String> topCategories = topTerms(categoryScores);
        List<String> topBrands = topTerms(brandScores);

        // Step 5: Query — personalized or featured fallback
        Page<Product> productPage;
        if (topCategories.isEmpty() && topBrands.isEmpty()) {
            productPage = productRepository.findFeaturedFeed(marketplaceId, pageable);
        } else {
            List<String> cats = topCategories.isEmpty() ? List.of(CATEGORY_SENTINEL) : topCategories;
            List<String> brands = topBrands.isEmpty() ? List.of(CATEGORY_SENTINEL) : topBrands;
            productPage = productRepository.findPersonalizedFeed(marketplaceId, cats, brands, excludedIds, pageable);
        }

        // Step 6: Map to DTOs
        List<Product> products = productPage.getContent();
        Map<UUID, MarketplaceVendor> vendorMap = buildVendorMap(marketplaceId, products);
        Map<UUID, ActivePromotionSummary> promoMap = activePromotionLookupService.findForProducts(products);

        return new PagedResponse<>(productPage.map(p ->
                toFeedResponse(p, vendorMap.get(p.getCompany().getId()), promoMap.get(p.getId()))));
    }

    private List<String> topTerms(Map<String, Long> scores) {
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(MAX_AFFINITY_TERMS)
                .map(Map.Entry::getKey)
                .toList();
    }

    private Map<UUID, MarketplaceVendor> buildVendorMap(UUID marketplaceId, List<Product> products) {
        Set<UUID> companyIds = products.stream()
                .map(p -> p.getCompany().getId())
                .collect(Collectors.toSet());
        if (companyIds.isEmpty()) return Map.of();
        return marketplaceVendorRepository
                .findByMarketplaceIdAndVendorCompanyIdIn(marketplaceId, companyIds)
                .stream()
                .collect(Collectors.toMap(mv -> mv.getVendorCompany().getId(), mv -> mv));
    }

    private MarketplaceCatalogProductResponse toFeedResponse(
            Product product, MarketplaceVendor vendor, ActivePromotionSummary activePromotion) {
        List<ProductImageResponse> images = product.getImages().stream()
                .map(this::toImageResponse)
                .toList();
        List<ProductVariantResponse> variants = product.getVariants().stream()
                .map(this::toVariantResponse)
                .toList();
        String vendorName = vendor != null ? vendor.getVendorCompany().getName() : null;
        String vendorTier = vendor != null ? vendor.getTier().name() : null;
        UUID vendorId     = vendor != null ? vendor.getId() : null;
        return new MarketplaceCatalogProductResponse(
                product.getId(),
                product.getCompany().getId(),
                product.getMarketplaceId(),
                vendorId,
                vendorName,
                vendorTier,
                product.getName(),
                product.getDescription(),
                product.getSku(),
                product.getPrice(),
                product.getCompareAtPrice(),
                product.getCurrency(),
                product.getCategory(),
                product.getBrand(),
                product.getTags(),
                product.getThumbnailUrl(),
                images,
                variants,
                product.getStock(),
                product.getStatus().name(),
                product.isFeatured(),
                product.isPurchasable(),
                product.getCreatedAt(),
                product.getUpdatedAt(),
                activePromotion
        );
    }

    private ProductImageResponse toImageResponse(ProductImage img) {
        return new ProductImageResponse(img.getId(), img.getImageUrl(), img.getDisplayOrder(), img.getCreatedAt());
    }

    private ProductVariantResponse toVariantResponse(ProductVariant v) {
        String title = Stream.of(v.getOption1(), v.getOption2(), v.getOption3())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" / "));
        return new ProductVariantResponse(
                v.getId(),
                v.getSku(),
                v.getPrice(),
                v.getCompareAtPrice(),
                v.getStock(),
                v.getLowStockThreshold(),
                v.isPurchasable(),
                v.isPreorderEnabled(),
                v.getPreorderExpectedDate(),
                v.getOption1(),
                v.getOption2(),
                v.getOption3(),
                title.isBlank() ? null : title,
                v.getDisplayOrder(),
                v.getCreatedAt(),
                v.getUpdatedAt()
        );
    }
}
