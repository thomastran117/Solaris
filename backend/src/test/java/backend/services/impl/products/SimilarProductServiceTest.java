package backend.services.impl.products;

import backend.documents.ProductDocument;
import backend.dtos.responses.product.SimilarProductResponse;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Company;
import backend.models.core.Product;
import backend.models.core.ProductRelationship;
import backend.models.enums.ProductRelationshipType;
import backend.models.enums.ProductStatus;
import backend.repositories.BundleRepository;
import backend.repositories.CollectionProductRepository;
import backend.repositories.CompanyRepository;
import backend.repositories.InventoryAdjustmentRepository;
import backend.repositories.MarketplaceProfileRepository;
import backend.repositories.MarketplaceVendorRepository;
import backend.repositories.ProductAttributeRepository;
import backend.repositories.ProductChangeLogRepository;
import backend.repositories.ProductImageRepository;
import backend.repositories.ProductOptionRepository;
import backend.repositories.ProductRelationshipRepository;
import backend.repositories.ProductRepository;
import backend.repositories.ProductReviewRepository;
import backend.repositories.ProductVariantRepository;
import backend.repositories.PromotionRuleRepository;
import backend.services.impl.SingleFlightCache;
import backend.services.impl.pricing.ActivePromotionLookupService;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.products.ProductChangeLogger;
import backend.testutil.TestIds;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SimilarProductServiceTest {

    private static final UUID COMPANY_ID  = TestIds.uuid(1);
    private static final UUID PRODUCT_ID  = TestIds.uuid(3);
    private static final UUID MANUAL_ID_1 = TestIds.uuid(4);
    private static final UUID MANUAL_ID_2 = TestIds.uuid(5);
    private static final UUID AUTO_ID_1   = TestIds.uuid(6);
    private static final UUID AUTO_ID_2   = TestIds.uuid(7);
    private static final UUID AUTO_ID_3   = TestIds.uuid(8);

    private ProductRepository productRepository;
    private CompanyRepository companyRepository;
    private ProductRelationshipRepository productRelationshipRepository;
    private ProductReviewRepository productReviewRepository;
    private ElasticsearchOperations elasticsearchOperations;
    private SingleFlightCache singleFlightCache;

    private ProductServiceImpl service;

    @SuppressWarnings({"unchecked", "rawtypes"})
    @BeforeEach
    void setUp() {
        productRepository             = mock(ProductRepository.class);
        companyRepository             = mock(CompanyRepository.class);
        productRelationshipRepository = mock(ProductRelationshipRepository.class);
        productReviewRepository       = mock(ProductReviewRepository.class);
        elasticsearchOperations       = mock(ElasticsearchOperations.class);
        singleFlightCache             = mock(SingleFlightCache.class);

        service = new ProductServiceImpl(
                productRepository,
                companyRepository,
                mock(ProductImageRepository.class),
                mock(ProductOptionRepository.class),
                mock(ProductVariantRepository.class),
                mock(ProductAttributeRepository.class),
                productReviewRepository,
                mock(BundleRepository.class),
                mock(CollectionProductRepository.class),
                mock(PromotionRuleRepository.class),
                mock(MarketplaceProfileRepository.class),
                mock(MarketplaceVendorRepository.class),
                mock(ApplicationEventPublisher.class),
                elasticsearchOperations,
                singleFlightCache,
                mock(ActivePromotionLookupService.class),
                mock(ProductChangeLogger.class),
                mock(ProductChangeLogRepository.class),
                mock(InventoryAdjustmentRepository.class),
                productRelationshipRepository,
                mock(CompanyAccessService.class),
                300L, 60L);

        when(companyRepository.existsById(COMPANY_ID)).thenReturn(true);
        when(productReviewRepository.findAverageRatingsByProductIds(any())).thenReturn(List.of());

        doAnswer(inv -> ((Supplier<?>) inv.getArgument(2)).get())
                .when(singleFlightCache).getOrLoad(anyString(), anyLong(), any(), any(Class.class));
        doAnswer(inv -> ((Supplier<?>) inv.getArgument(2)).get())
                .when(singleFlightCache).getOrLoad(anyString(), anyLong(), any(), any(TypeReference.class));
    }

    // ─── getSimilarProducts ───────────────────────────────────────────────────

    @Test
    void getSimilarProducts_manualsOnly_returnsManualSource() {
        Product source = makeProduct(PRODUCT_ID, "Electronics", new BigDecimal("100.00"));
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(source));

        Product m1 = makeProduct(MANUAL_ID_1, "Electronics", new BigDecimal("90.00"));
        Product m2 = makeProduct(MANUAL_ID_2, "Electronics", new BigDecimal("110.00"));
        ProductRelationship rel1 = makeRelationship(source, m1);
        ProductRelationship rel2 = makeRelationship(source, m2);
        when(productRelationshipRepository.findAllBySourceProductIdAndTypeAndSourceProductCompanyId(
                PRODUCT_ID, ProductRelationshipType.SIMILAR, COMPANY_ID))
                .thenReturn(List.of(rel1, rel2));
        when(productRepository.findAllByIdInAndCompanyId(List.of(MANUAL_ID_1, MANUAL_ID_2), COMPANY_ID))
                .thenReturn(List.of(m1, m2));

        List<SimilarProductResponse> result = service.getSimilarProducts(COMPANY_ID, PRODUCT_ID, 2);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(r -> "MANUAL".equals(r.source())));
        verify(elasticsearchOperations, never()).search(any(Query.class), any());
    }

    @Test
    void getSimilarProducts_noManuals_returnsAutoFromES() {
        Product source = makeProduct(PRODUCT_ID, "Electronics", new BigDecimal("100.00"));
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(source));
        when(productRelationshipRepository.findAllBySourceProductIdAndTypeAndSourceProductCompanyId(
                PRODUCT_ID, ProductRelationshipType.SIMILAR, COMPANY_ID))
                .thenReturn(List.of());

        Product auto1 = makeProduct(AUTO_ID_1, "Electronics", new BigDecimal("95.00"));
        Product auto2 = makeProduct(AUTO_ID_2, "Electronics", new BigDecimal("105.00"));
        stubEsSearch(AUTO_ID_1, AUTO_ID_2);
        when(productRepository.findAllByIdInAndCompanyId(anyList(), eq(COMPANY_ID)))
                .thenReturn(List.of(auto1, auto2));

        List<SimilarProductResponse> result = service.getSimilarProducts(COMPANY_ID, PRODUCT_ID, 2);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(r -> "AUTO".equals(r.source())));
    }

    @Test
    void getSimilarProducts_manualPlusAutoFill_combinesBothSources() {
        Product source = makeProduct(PRODUCT_ID, "Fitness", new BigDecimal("50.00"));
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(source));

        Product m1 = makeProduct(MANUAL_ID_1, "Fitness", new BigDecimal("45.00"));
        Product m2 = makeProduct(MANUAL_ID_2, "Fitness", new BigDecimal("55.00"));
        ProductRelationship rel1 = makeRelationship(source, m1);
        ProductRelationship rel2 = makeRelationship(source, m2);
        when(productRelationshipRepository.findAllBySourceProductIdAndTypeAndSourceProductCompanyId(
                PRODUCT_ID, ProductRelationshipType.SIMILAR, COMPANY_ID))
                .thenReturn(List.of(rel1, rel2));
        when(productRepository.findAllByIdInAndCompanyId(List.of(MANUAL_ID_1, MANUAL_ID_2), COMPANY_ID))
                .thenReturn(List.of(m1, m2));

        Product auto1 = makeProduct(AUTO_ID_1, "Fitness", new BigDecimal("48.00"));
        Product auto2 = makeProduct(AUTO_ID_2, "Fitness", new BigDecimal("52.00"));
        Product auto3 = makeProduct(AUTO_ID_3, "Fitness", new BigDecimal("60.00"));
        stubEsSearch(AUTO_ID_1, AUTO_ID_2, AUTO_ID_3);
        when(productRepository.findAllByIdInAndCompanyId(
                argThat(ids -> ids != null && ids.contains(AUTO_ID_1)), eq(COMPANY_ID)))
                .thenReturn(List.of(auto1, auto2, auto3));

        List<SimilarProductResponse> result = service.getSimilarProducts(COMPANY_ID, PRODUCT_ID, 5);

        assertEquals(5, result.size());
        long manualCount = result.stream().filter(r -> "MANUAL".equals(r.source())).count();
        long autoCount   = result.stream().filter(r -> "AUTO".equals(r.source())).count();
        assertEquals(2, manualCount);
        assertEquals(3, autoCount);
    }

    @Test
    void getSimilarProducts_limitReachedByManuals_esNotQueried() {
        Product source = makeProduct(PRODUCT_ID, "Electronics", new BigDecimal("200.00"));
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(source));

        Product m1 = makeProduct(MANUAL_ID_1, "Electronics", new BigDecimal("190.00"));
        Product m2 = makeProduct(MANUAL_ID_2, "Electronics", new BigDecimal("210.00"));
        ProductRelationship rel1 = makeRelationship(source, m1);
        ProductRelationship rel2 = makeRelationship(source, m2);
        when(productRelationshipRepository.findAllBySourceProductIdAndTypeAndSourceProductCompanyId(
                PRODUCT_ID, ProductRelationshipType.SIMILAR, COMPANY_ID))
                .thenReturn(List.of(rel1, rel2));
        when(productRepository.findAllByIdInAndCompanyId(List.of(MANUAL_ID_1, MANUAL_ID_2), COMPANY_ID))
                .thenReturn(List.of(m1, m2));

        List<SimilarProductResponse> result = service.getSimilarProducts(COMPANY_ID, PRODUCT_ID, 2);

        assertEquals(2, result.size());
        verify(elasticsearchOperations, never()).search(any(Query.class), any());
    }

    @Test
    void getSimilarProducts_productNotFound_throwsNotFoundException() {
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.empty());
        when(productRelationshipRepository.findAllBySourceProductIdAndTypeAndSourceProductCompanyId(
                any(), any(), any())).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getSimilarProducts(COMPANY_ID, PRODUCT_ID, 8));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getSimilarProducts_esDown_fallsBackToJpa() {
        Product source = makeProduct(PRODUCT_ID, "Home", new BigDecimal("75.00"));
        when(productRepository.findByIdAndCompanyId(PRODUCT_ID, COMPANY_ID)).thenReturn(Optional.of(source));
        when(productRelationshipRepository.findAllBySourceProductIdAndTypeAndSourceProductCompanyId(
                PRODUCT_ID, ProductRelationshipType.SIMILAR, COMPANY_ID))
                .thenReturn(List.of());

        when(elasticsearchOperations.search(any(Query.class), eq(ProductDocument.class)))
                .thenThrow(new RuntimeException("ES unavailable"));

        Product fallback = makeProduct(AUTO_ID_1, "Home", new BigDecimal("80.00"));
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(fallback)));
        when(productRepository.findAllByIdInAndCompanyId(anyList(), eq(COMPANY_ID)))
                .thenReturn(List.of(fallback));

        List<SimilarProductResponse> result = service.getSimilarProducts(COMPANY_ID, PRODUCT_ID, 5);

        assertFalse(result.isEmpty());
        assertEquals("AUTO", result.get(0).source());
        verify(productRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private static Product makeProduct(UUID id, String category, BigDecimal price) {
        Company company = new Company();
        company.setId(COMPANY_ID);

        Product p = new Product();
        p.setId(id);
        p.setName("Product-" + id);
        p.setSku("SKU-" + id);
        p.setPrice(price);
        p.setCurrency("USD");
        p.setCategory(category);
        p.setStatus(ProductStatus.ACTIVE);
        p.setCompany(company);
        return p;
    }

    private static ProductRelationship makeRelationship(Product source, Product target) {
        ProductRelationship rel = new ProductRelationship();
        rel.setSourceProduct(source);
        rel.setTargetProduct(target);
        rel.setType(ProductRelationshipType.SIMILAR);
        return rel;
    }

    @SuppressWarnings("unchecked")
    private void stubEsSearch(UUID... ids) {
        SearchHits<ProductDocument> mockHits = mock(SearchHits.class);
        Stream<SearchHit<ProductDocument>> hitStream = Stream.of(ids).map(id -> {
            SearchHit<ProductDocument> hit = mock(SearchHit.class);
            ProductDocument doc = new ProductDocument();
            doc.setId(id);
            when(hit.getContent()).thenReturn(doc);
            return hit;
        });
        when(mockHits.stream()).thenReturn(hitStream);
        when(elasticsearchOperations.search(any(Query.class), eq(ProductDocument.class)))
                .thenReturn(mockHits);
    }
}
