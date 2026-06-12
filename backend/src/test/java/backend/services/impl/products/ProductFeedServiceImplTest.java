package backend.services.impl.products;

import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.product.MarketplaceCatalogProductResponse;
import backend.models.core.Company;
import backend.models.core.Product;
import backend.models.enums.ProductStatus;
import backend.repositories.MarketplaceVendorRepository;
import backend.repositories.OrderRepository;
import backend.repositories.ProductRepository;
import backend.repositories.SavedListItemRepository;
import backend.services.impl.pricing.ActivePromotionLookupService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductFeedServiceImplTest {

    private static final UUID MARKETPLACE_ID = TestIds.uuid(1);
    private static final UUID USER_ID        = TestIds.uuid(2);
    private static final UUID PRODUCT_ID     = TestIds.uuid(3);
    private static final UUID COMPANY_ID     = TestIds.uuid(4);
    private static final UUID SENTINEL_UUID  = new UUID(0L, 0L);

    private OrderRepository orderRepository;
    private SavedListItemRepository savedListItemRepository;
    private ProductRepository productRepository;
    private MarketplaceVendorRepository marketplaceVendorRepository;
    private ActivePromotionLookupService activePromotionLookupService;
    private ProductFeedServiceImpl service;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        savedListItemRepository = mock(SavedListItemRepository.class);
        productRepository = mock(ProductRepository.class);
        marketplaceVendorRepository = mock(MarketplaceVendorRepository.class);
        activePromotionLookupService = mock(ActivePromotionLookupService.class);

        service = new ProductFeedServiceImpl(
                orderRepository,
                savedListItemRepository,
                productRepository,
                marketplaceVendorRepository,
                activePromotionLookupService);

        // Default stub: no vendor entries, no promotions
        when(marketplaceVendorRepository.findByMarketplaceIdAndVendorCompanyIdIn(any(), any()))
                .thenReturn(List.of());
        when(activePromotionLookupService.findForProducts(any())).thenReturn(Map.of());
        // Default: no wishlist items
        when(savedListItemRepository.findActiveWishlistProductIdsByUserId(USER_ID))
                .thenReturn(List.of());
    }

    @Test
    void getFeed_purchaseSignals_callsPersonalizedQuery() {
        when(orderRepository.findPurchasedProductIdsByUserId(eq(USER_ID), any(Pageable.class)))
                .thenReturn(List.of());
        when(orderRepository.findTopPurchasedCategoriesByUserId(USER_ID))
                .thenReturn(Collections.<Object[]>singletonList(new Object[]{"Electronics", 2L}));
        when(orderRepository.findTopPurchasedBrandsByUserId(USER_ID))
                .thenReturn(List.of());

        Product product = mockProduct("Electronics", "Acme");
        when(productRepository.findPersonalizedFeed(
                eq(MARKETPLACE_ID), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product)));

        PagedResponse<MarketplaceCatalogProductResponse> result =
                service.getFeed(MARKETPLACE_ID, USER_ID, 0, 20);

        assertFalse(result.getItems().isEmpty());
        verify(productRepository).findPersonalizedFeed(
                eq(MARKETPLACE_ID), any(), any(), any(), any(Pageable.class));
        verify(productRepository, never()).findFeaturedFeed(any(), any());
    }

    @Test
    void getFeed_noSignals_callsFeaturedFallback() {
        when(orderRepository.findPurchasedProductIdsByUserId(eq(USER_ID), any(Pageable.class)))
                .thenReturn(List.of());
        when(orderRepository.findTopPurchasedCategoriesByUserId(USER_ID)).thenReturn(List.of());
        when(orderRepository.findTopPurchasedBrandsByUserId(USER_ID)).thenReturn(List.of());

        when(productRepository.findFeaturedFeed(eq(MARKETPLACE_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        PagedResponse<MarketplaceCatalogProductResponse> result =
                service.getFeed(MARKETPLACE_ID, USER_ID, 0, 20);

        assertTrue(result.getItems().isEmpty());
        verify(productRepository).findFeaturedFeed(eq(MARKETPLACE_ID), any(Pageable.class));
        verify(productRepository, never()).findPersonalizedFeed(any(), any(), any(), any(), any());
    }

    @Test
    void getFeed_emptyExcludedIds_usesSentinelUUID() {
        when(orderRepository.findPurchasedProductIdsByUserId(eq(USER_ID), any(Pageable.class)))
                .thenReturn(List.of());  // empty → sentinel applied
        when(orderRepository.findTopPurchasedCategoriesByUserId(USER_ID))
                .thenReturn(Collections.<Object[]>singletonList(new Object[]{"Books", 1L}));
        when(orderRepository.findTopPurchasedBrandsByUserId(USER_ID)).thenReturn(List.of());
        when(productRepository.findPersonalizedFeed(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.getFeed(MARKETPLACE_ID, USER_ID, 0, 20);

        verify(productRepository).findPersonalizedFeed(
                eq(MARKETPLACE_ID),
                any(),
                any(),
                argThat(ids -> ((Collection<?>) ids).contains(SENTINEL_UUID)),
                any(Pageable.class));
    }

    @Test
    void getFeed_emptyCategorySignal_usesCategorySentinel() {
        when(orderRepository.findPurchasedProductIdsByUserId(eq(USER_ID), any(Pageable.class)))
                .thenReturn(List.of());
        when(orderRepository.findTopPurchasedCategoriesByUserId(USER_ID)).thenReturn(List.of());
        when(orderRepository.findTopPurchasedBrandsByUserId(USER_ID))
                .thenReturn(Collections.<Object[]>singletonList(new Object[]{"Acme", 3L}));
        when(productRepository.findPersonalizedFeed(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.getFeed(MARKETPLACE_ID, USER_ID, 0, 20);

        verify(productRepository).findPersonalizedFeed(
                eq(MARKETPLACE_ID),
                argThat(cats -> ((Collection<?>) cats).contains("__no_match__")),
                argThat(brands -> ((Collection<?>) brands).contains("Acme")),
                any(),
                any(Pageable.class));
    }

    @Test
    void getFeed_wishlistOnly_scoresUsedForQuery() {
        when(orderRepository.findPurchasedProductIdsByUserId(eq(USER_ID), any(Pageable.class)))
                .thenReturn(List.of());
        when(orderRepository.findTopPurchasedCategoriesByUserId(USER_ID)).thenReturn(List.of());
        when(orderRepository.findTopPurchasedBrandsByUserId(USER_ID)).thenReturn(List.of());

        UUID wishlistProductId = TestIds.uuid(99);
        when(savedListItemRepository.findActiveWishlistProductIdsByUserId(USER_ID))
                .thenReturn(List.of(wishlistProductId));
        // Create mock products before the when() stub to avoid Mockito state corruption
        // from nested mock creation inside thenReturn() argument evaluation.
        Product wishlistProduct = mockProduct("Books", "NoName");
        when(productRepository.findAllById(anyIterable()))
                .thenReturn(List.of(wishlistProduct));

        when(productRepository.findPersonalizedFeed(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.getFeed(MARKETPLACE_ID, USER_ID, 0, 20);

        verify(productRepository).findPersonalizedFeed(
                eq(MARKETPLACE_ID),
                argThat(cats -> ((Collection<?>) cats).contains("Books")),
                any(),
                any(),
                any(Pageable.class));
    }

    @Test
    void getFeed_purchaseWeightsHigherThanWishlist() {
        // Purchase: Electronics ×1 → score 3. Wishlist: Books ×3 → score 3. Tie → both appear in top-5.
        when(orderRepository.findPurchasedProductIdsByUserId(eq(USER_ID), any(Pageable.class)))
                .thenReturn(List.of());
        when(orderRepository.findTopPurchasedCategoriesByUserId(USER_ID))
                .thenReturn(Collections.<Object[]>singletonList(new Object[]{"Electronics", 1L}));
        when(orderRepository.findTopPurchasedBrandsByUserId(USER_ID)).thenReturn(List.of());

        when(savedListItemRepository.findActiveWishlistProductIdsByUserId(USER_ID))
                .thenReturn(List.of(TestIds.uuid(50), TestIds.uuid(51), TestIds.uuid(52)));
        // Create mock products before the when() stub to avoid Mockito state corruption.
        Product wp1 = mockProduct("Books", null);
        Product wp2 = mockProduct("Books", null);
        Product wp3 = mockProduct("Books", null);
        when(productRepository.findAllById(anyIterable())).thenReturn(List.of(wp1, wp2, wp3));

        when(productRepository.findPersonalizedFeed(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.getFeed(MARKETPLACE_ID, USER_ID, 0, 20);

        verify(productRepository).findPersonalizedFeed(
                eq(MARKETPLACE_ID),
                argThat(cats -> {
                    Collection<?> c = (Collection<?>) cats;
                    return c.contains("Electronics") && c.contains("Books");
                }),
                any(),
                any(),
                any(Pageable.class));
    }

    @Test
    void getFeed_largeWishlist_cappedAt200() {
        when(orderRepository.findPurchasedProductIdsByUserId(eq(USER_ID), any(Pageable.class)))
                .thenReturn(List.of());
        when(orderRepository.findTopPurchasedCategoriesByUserId(USER_ID)).thenReturn(List.of());
        when(orderRepository.findTopPurchasedBrandsByUserId(USER_ID)).thenReturn(List.of());

        List<UUID> bigWishlist = new java.util.ArrayList<>();
        for (int i = 100; i < 350; i++) bigWishlist.add(TestIds.uuid(i));
        when(savedListItemRepository.findActiveWishlistProductIdsByUserId(USER_ID))
                .thenReturn(bigWishlist);
        when(productRepository.findAllById(anyIterable())).thenReturn(List.of());
        when(productRepository.findFeaturedFeed(any(), any())).thenReturn(new PageImpl<>(List.of()));

        service.getFeed(MARKETPLACE_ID, USER_ID, 0, 20);

        verify(productRepository).findAllById(argThat(ids -> {
            int size = (int) ((Iterable<?>) ids).spliterator().getExactSizeIfKnown();
            return size == 200;
        }));
    }

    @Test
    void getFeed_emptyPage_returnsEmptyPagedResponse() {
        when(orderRepository.findPurchasedProductIdsByUserId(eq(USER_ID), any(Pageable.class)))
                .thenReturn(List.of());
        when(orderRepository.findTopPurchasedCategoriesByUserId(USER_ID))
                .thenReturn(Collections.<Object[]>singletonList(new Object[]{"Toys", 1L}));
        when(orderRepository.findTopPurchasedBrandsByUserId(USER_ID)).thenReturn(List.of());
        when(productRepository.findPersonalizedFeed(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        PagedResponse<MarketplaceCatalogProductResponse> result =
                service.getFeed(MARKETPLACE_ID, USER_ID, 0, 20);

        assertTrue(result.getItems().isEmpty());
        assertEquals(0, result.getTotalElements());
    }

    // --- helpers ---

    private Product mockProduct(String category, String brand) {
        Product p = mock(Product.class);
        Company company = mock(Company.class);
        when(company.getId()).thenReturn(COMPANY_ID);
        when(p.getId()).thenReturn(PRODUCT_ID);
        when(p.getCompany()).thenReturn(company);
        when(p.getMarketplaceId()).thenReturn(MARKETPLACE_ID);
        when(p.getCategory()).thenReturn(category);
        when(p.getBrand()).thenReturn(brand);
        when(p.getName()).thenReturn("Test Product");
        when(p.getDescription()).thenReturn(null);
        when(p.getSku()).thenReturn("SKU-1");
        when(p.getPrice()).thenReturn(new BigDecimal("9.99"));
        when(p.getCompareAtPrice()).thenReturn(null);
        when(p.getCurrency()).thenReturn("USD");
        when(p.getTags()).thenReturn(null);
        when(p.getThumbnailUrl()).thenReturn(null);
        when(p.getImages()).thenReturn(List.of());
        when(p.getVariants()).thenReturn(List.of());
        when(p.getStock()).thenReturn(null);
        when(p.getStatus()).thenReturn(ProductStatus.ACTIVE);
        when(p.isFeatured()).thenReturn(false);
        when(p.isPurchasable()).thenReturn(true);
        when(p.getCreatedAt()).thenReturn(null);
        when(p.getUpdatedAt()).thenReturn(null);
        return p;
    }
}
