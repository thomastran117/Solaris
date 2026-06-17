package backend.services.impl.products;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import com.fasterxml.jackson.core.type.TypeReference;

import backend.dtos.responses.product.ComparedProduct;
import backend.dtos.responses.product.ComparisonRow;
import backend.dtos.responses.product.ProductComparisonResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Product;
import backend.models.core.ProductAttribute;
import backend.models.enums.ProductStatus;
import backend.repositories.ProductRepository;
import backend.repositories.ProductReviewRepository;
import backend.services.impl.SingleFlightCache;
import backend.testutil.TestIds;

class ProductComparisonServiceTest {

    private static final UUID MARKETPLACE_ID = TestIds.uuid(1);
    private static final UUID P1 = TestIds.uuid(10);
    private static final UUID P2 = TestIds.uuid(20);
    private static final UUID P3 = TestIds.uuid(30);

    private ProductRepository productRepository;
    private ProductReviewRepository productReviewRepository;
    private SingleFlightCache singleFlightCache;
    private PlatformTransactionManager transactionManager;
    private ProductComparisonServiceImpl service;

    @BeforeEach
    void setUp() {
        productRepository = mock(ProductRepository.class);
        productReviewRepository = mock(ProductReviewRepository.class);
        singleFlightCache = mock(SingleFlightCache.class);
        transactionManager = mock(PlatformTransactionManager.class);

        // Bypass cache: execute the loader directly.
        doAnswer(inv -> ((Supplier<?>) inv.getArgument(2)).get())
                .when(singleFlightCache).getOrLoad(anyString(), anyLong(), any(), any(TypeReference.class));

        when(productReviewRepository.findAverageRatingsByProductIds(any())).thenReturn(List.of());

        service = new ProductComparisonServiceImpl(
                productRepository, productReviewRepository, singleFlightCache, transactionManager, 120);
    }

    @Test
    void compare_twoProducts_returnsColumnsAndOneRowPerDistinctAttribute() {
        Product p1 = makeProduct(P1, "Alpha");
        addAttribute(p1, "Material", "Steel", 0);
        addAttribute(p1, "Weight", "2kg", 1);
        Product p2 = makeProduct(P2, "Beta");
        addAttribute(p2, "Material", "Aluminium", 0);

        when(productRepository.findAllByIdInAndMarketplaceIdWithAttributes(any(), any()))
                .thenReturn(List.of(p1, p2));

        ProductComparisonResponse result = service.compare(MARKETPLACE_ID, List.of(P1, P2));

        assertEquals(2, result.products().size());
        // Distinct attribute keys across both products: Material + Weight.
        assertEquals(2, result.attributes().size());
        assertEquals(List.of("Material", "Weight"),
                result.attributes().stream().map(ComparisonRow::attributeName).toList());
    }

    @Test
    void compare_attributeMissingOnOneProduct_rowValueIsNullForThatProduct() {
        Product p1 = makeProduct(P1, "Alpha");
        addAttribute(p1, "Material", "Steel", 0);
        Product p2 = makeProduct(P2, "Beta"); // no Material attribute

        when(productRepository.findAllByIdInAndMarketplaceIdWithAttributes(any(), any()))
                .thenReturn(List.of(p1, p2));

        ProductComparisonResponse result = service.compare(MARKETPLACE_ID, List.of(P1, P2));

        ComparisonRow material = result.attributes().get(0);
        assertEquals("Material", material.attributeName());
        assertEquals("Steel", material.valuesByProductId().get(P1));
        assertTrue(material.valuesByProductId().containsKey(P2));
        assertNull(material.valuesByProductId().get(P2));
    }

    @Test
    void compare_fewerThanTwoIds_throwsBadRequest() {
        assertThrows(BadRequestException.class,
                () -> service.compare(MARKETPLACE_ID, List.of(P1)));
    }

    @Test
    void compare_moreThanFourIds_throwsBadRequest() {
        assertThrows(BadRequestException.class,
                () -> service.compare(MARKETPLACE_ID,
                        List.of(TestIds.uuid(1), TestIds.uuid(2), TestIds.uuid(3),
                                TestIds.uuid(4), TestIds.uuid(5))));
    }

    @Test
    void compare_duplicateIdsCollapseToOne_throwsBadRequest() {
        assertThrows(BadRequestException.class,
                () -> service.compare(MARKETPLACE_ID, List.of(P1, P1)));
    }

    @Test
    void compare_idNotInMarketplace_throwsResourceNotFound() {
        Product p1 = makeProduct(P1, "Alpha");
        when(productRepository.findAllByIdInAndMarketplaceIdWithAttributes(any(), any()))
                .thenReturn(List.of(p1)); // P2 absent

        assertThrows(ResourceNotFoundException.class,
                () -> service.compare(MARKETPLACE_ID, List.of(P1, P2)));
    }

    @Test
    void compare_nonPublicProductExcluded_throwsResourceNotFound() {
        Product active = makeProduct(P1, "Alpha");
        Product draft = makeProduct(P2, "Beta");
        draft.setStatus(ProductStatus.DRAFT);
        when(productRepository.findAllByIdInAndMarketplaceIdWithAttributes(any(), any()))
                .thenReturn(List.of(active, draft));

        // The DRAFT product is filtered out, so id P2 is "not available" -> 404.
        assertThrows(ResourceNotFoundException.class,
                () -> service.compare(MARKETPLACE_ID, List.of(P1, P2)));
    }

    @Test
    void compare_populatesCoreFieldsAndStockStatus() {
        Product inStock = makeProduct(P1, "Alpha");
        inStock.setStock(50);
        inStock.setThumbnailUrl("https://img/alpha.png");
        Product lowStock = makeProduct(P2, "Beta");
        lowStock.setStock(2);
        lowStock.setLowStockThreshold(5);
        Product outOfStock = makeProduct(P3, "Gamma");
        outOfStock.setStock(0);

        when(productRepository.findAllByIdInAndMarketplaceIdWithAttributes(any(), any()))
                .thenReturn(List.of(inStock, lowStock, outOfStock));

        ProductComparisonResponse result = service.compare(MARKETPLACE_ID, List.of(P1, P2, P3));

        ComparedProduct a = result.products().get(0);
        assertEquals("Alpha", a.name());
        assertEquals(new BigDecimal("29.99"), a.price());
        assertEquals("USD", a.currency());
        assertEquals("IN_STOCK", a.stockStatus());
        assertEquals("https://img/alpha.png", a.imageUrl());
        assertEquals("LOW_STOCK", result.products().get(1).stockStatus());
        assertEquals("OUT_OF_STOCK", result.products().get(2).stockStatus());
    }

    @Test
    void compare_noReviews_ratingIsNullAndCountZero() {
        Product p1 = makeProduct(P1, "Alpha");
        Product p2 = makeProduct(P2, "Beta");
        when(productRepository.findAllByIdInAndMarketplaceIdWithAttributes(any(), any()))
                .thenReturn(List.of(p1, p2));

        ProductComparisonResponse result = service.compare(MARKETPLACE_ID, List.of(P1, P2));

        ComparedProduct a = result.products().get(0);
        assertNull(a.rating());
        assertEquals(0L, a.reviewCount());
    }

    @Test
    void compare_withReviews_populatesRatingAndCount() {
        Product p1 = makeProduct(P1, "Alpha");
        Product p2 = makeProduct(P2, "Beta");
        when(productRepository.findAllByIdInAndMarketplaceIdWithAttributes(any(), any()))
                .thenReturn(List.of(p1, p2));
        when(productReviewRepository.findAverageRatingsByProductIds(any()))
                .thenReturn(List.<Object[]>of(new Object[]{P1, 4.5, 12L}));

        ProductComparisonResponse result = service.compare(MARKETPLACE_ID, List.of(P1, P2));

        ComparedProduct a = result.products().get(0);
        assertNotNull(a.rating());
        assertEquals(4.5, a.rating(), 0.0001);
        assertEquals(12L, a.reviewCount());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Product makeProduct(UUID id, String name) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setPrice(new BigDecimal("29.99"));
        p.setCurrency("USD");
        p.setStatus(ProductStatus.ACTIVE);
        p.setMarketplaceListed(true);
        p.setMarketplaceId(MARKETPLACE_ID);
        p.setImages(new ArrayList<>());
        p.setAttributes(new ArrayList<>());
        return p;
    }

    private void addAttribute(Product p, String name, String value, int order) {
        ProductAttribute a = new ProductAttribute();
        a.setName(name);
        a.setValue(value);
        a.setDisplayOrder(order);
        a.setProduct(p);
        p.getAttributes().add(a);
    }
}
