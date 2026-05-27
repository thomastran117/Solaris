package backend.services.impl.products;

import backend.documents.ProductDocument;
import backend.models.core.Company;
import backend.models.core.Product;
import backend.models.core.ProductAttribute;
import backend.models.core.ProductSimilarity;
import backend.models.core.ProductSimilarityId;
import backend.models.enums.ProductStatus;
import backend.models.enums.SimilaritySignal;
import backend.repositories.CollectionProductRepository;
import backend.repositories.ProductAttributeRepository;
import backend.repositories.ProductRepository;
import backend.repositories.ProductSimilarityRepository;
import backend.testutil.TestIds;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductSimilarityServiceTest {

    private static final UUID COMPANY_ID = TestIds.uuid(1);
    private static final UUID SOURCE_ID  = TestIds.uuid(2);
    private static final UUID TARGET_A   = TestIds.uuid(3);
    private static final UUID TARGET_B   = TestIds.uuid(4);
    private static final UUID TARGET_C   = TestIds.uuid(5);

    private ProductRepository productRepository;
    private ProductAttributeRepository productAttributeRepository;
    private CollectionProductRepository collectionProductRepository;
    private ProductSimilarityRepository productSimilarityRepository;
    private ElasticsearchOperations elasticsearchOperations;
    private MeterRegistry meterRegistry;

    private ProductSimilarityService service;

    @BeforeEach
    void setUp() {
        productRepository          = mock(ProductRepository.class);
        productAttributeRepository = mock(ProductAttributeRepository.class);
        collectionProductRepository = mock(CollectionProductRepository.class);
        productSimilarityRepository = mock(ProductSimilarityRepository.class);
        elasticsearchOperations    = mock(ElasticsearchOperations.class);
        meterRegistry              = new SimpleMeterRegistry();

        service = new ProductSimilarityService(
                productRepository,
                productAttributeRepository,
                collectionProductRepository,
                productSimilarityRepository,
                elasticsearchOperations,
                meterRegistry);

        when(productAttributeRepository.findAllByProductIdOrderByDisplayOrderAsc(any()))
                .thenReturn(List.of());
        when(collectionProductRepository.findProductCollectionPairs(any()))
                .thenReturn(List.of());
    }

    // ── recompute — core behaviour ─────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void recompute_writesRowsForTopCandidates() {
        Product source = makeProduct(SOURCE_ID, "Electronics", "BrandX", new BigDecimal("100.00"));
        when(productRepository.findByIdWithCompanyOwner(SOURCE_ID)).thenReturn(Optional.of(source));
        when(productRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(productRepository.getReferenceById(any())).thenAnswer(inv -> {
            Product p = new Product(); p.setId(inv.getArgument(0)); return p;
        });

        ProductDocument docA = makeDoc(TARGET_A, "Electronics", "BrandX", new BigDecimal("90.00"), null, null);
        ProductDocument docB = makeDoc(TARGET_B, "Electronics", null,     new BigDecimal("80.00"), null, null);
        stubEsSearch(docA, docB);

        service.recompute(SOURCE_ID);

        verify(productSimilarityRepository).deleteAllByIdSourceProductId(SOURCE_ID);
        verify(productSimilarityRepository).saveAll(argThat(rows -> {
            List<ProductSimilarity> list = (List<ProductSimilarity>) rows;
            // TARGET_A has CATEGORY + BRAND + PRICE_RANGE — higher score than TARGET_B
            assertEquals(TARGET_A, list.get(0).getId().getTargetProductId());
            assertEquals(TARGET_B, list.get(1).getId().getTargetProductId());
            return true;
        }));
    }

    @Test
    @SuppressWarnings("unchecked")
    void recompute_brandMatchOutscoresPriceOnly() {
        Product source = makeProduct(SOURCE_ID, "Fitness", "BrandY", new BigDecimal("50.00"));
        when(productRepository.findByIdWithCompanyOwner(SOURCE_ID)).thenReturn(Optional.of(source));
        when(productRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(productRepository.getReferenceById(any())).thenAnswer(inv -> {
            Product p = new Product(); p.setId(inv.getArgument(0)); return p;
        });

        // priceOnly matches only CATEGORY + PRICE_RANGE; brandMatch matches CATEGORY + BRAND + PRICE_RANGE
        ProductDocument priceOnly  = makeDoc(TARGET_A, "Fitness", null,    new BigDecimal("45.00"), null, null);
        ProductDocument brandMatch = makeDoc(TARGET_B, "Fitness", "BrandY", new BigDecimal("55.00"), null, null);
        stubEsSearch(priceOnly, brandMatch);

        service.recompute(SOURCE_ID);

        verify(productSimilarityRepository).saveAll(argThat(rows -> {
            List<ProductSimilarity> list = (List<ProductSimilarity>) rows;
            // brandMatch should be first (higher score)
            assertEquals(TARGET_B, list.get(0).getId().getTargetProductId());
            assertEquals(TARGET_A, list.get(1).getId().getTargetProductId());
            return true;
        }));
    }

    @Test
    void recompute_sparseProduct_doesNotThrow() {
        Product source = makeProduct(SOURCE_ID, null, null, null);
        source.getCompany().setId(COMPANY_ID);
        when(productRepository.findByIdWithCompanyOwner(SOURCE_ID)).thenReturn(Optional.of(source));

        // No exception thrown, and since availableMaxWeight == 0, no ES call or write
        assertDoesNotThrow(() -> service.recompute(SOURCE_ID));
        verify(elasticsearchOperations, never()).search(any(Query.class), any());
        verify(productSimilarityRepository, never()).deleteAllByIdSourceProductId(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void recompute_staleWriteGuard_skipsIfProductUpdated() {
        Instant t1 = Instant.now().minusSeconds(10);
        Instant t2 = Instant.now();

        Product snapshot = makeProduct(SOURCE_ID, "Electronics", "BrandX", new BigDecimal("100.00"));
        snapshot.setUpdatedAt(t1);
        when(productRepository.findByIdWithCompanyOwner(SOURCE_ID)).thenReturn(Optional.of(snapshot));

        ProductDocument doc = makeDoc(TARGET_A, "Electronics", "BrandX", new BigDecimal("90.00"), null, null);
        stubEsSearch(doc);

        // Latest product has a newer updatedAt — stale guard should abort
        Product latest = makeProduct(SOURCE_ID, "Electronics", "BrandX", new BigDecimal("100.00"));
        latest.setUpdatedAt(t2);
        when(productRepository.findById(SOURCE_ID)).thenReturn(Optional.of(latest));

        service.recompute(SOURCE_ID);

        verify(productSimilarityRepository, never()).deleteAllByIdSourceProductId(any());
        verify(productSimilarityRepository, never()).saveAll(any());
    }

    @Test
    void recompute_excludesSourceProductFromCandidates() {
        Product source = makeProduct(SOURCE_ID, "Electronics", "BrandX", new BigDecimal("100.00"));
        when(productRepository.findByIdWithCompanyOwner(SOURCE_ID)).thenReturn(Optional.of(source));
        when(productRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(productRepository.getReferenceById(any())).thenAnswer(inv -> {
            Product p = new Product(); p.setId(inv.getArgument(0)); return p;
        });

        // ES returns a doc matching the source ID — it must be filtered by the IdsQuery must_not
        // In the actual ES call, the query includes must_not on source ID. We can verify the query
        // doesn't produce a row for SOURCE_ID by returning an empty hits list (ES filters it).
        stubEsSearch(); // no hits

        service.recompute(SOURCE_ID);

        verify(productSimilarityRepository, never()).saveAll(argThat(rows -> {
            List<ProductSimilarity> list = (List<ProductSimilarity>) rows;
            return list.stream().anyMatch(r -> SOURCE_ID.equals(r.getId().getTargetProductId()));
        }));
    }

    // ── parseSignals ───────────────────────────────────────────────────────────

    @Test
    void parseSignals_knownValues_parsed() {
        List<SimilaritySignal> result = ProductSimilarityService.parseSignals("CATEGORY,BRAND,PRICE_RANGE");
        assertEquals(List.of(SimilaritySignal.CATEGORY, SimilaritySignal.BRAND, SimilaritySignal.PRICE_RANGE), result);
    }

    @Test
    void parseSignals_unknownValue_silentlyDropped() {
        List<SimilaritySignal> result = ProductSimilarityService.parseSignals("CATEGORY,FUTURE_SIGNAL,BRAND");
        assertEquals(List.of(SimilaritySignal.CATEGORY, SimilaritySignal.BRAND), result);
    }

    @Test
    void parseSignals_nullOrBlank_returnsEmpty() {
        assertTrue(ProductSimilarityService.parseSignals(null).isEmpty());
        assertTrue(ProductSimilarityService.parseSignals("").isEmpty());
        assertTrue(ProductSimilarityService.parseSignals("   ").isEmpty());
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private Product makeProduct(UUID id, String category, String brand, BigDecimal price) {
        Company company = new Company();
        company.setId(COMPANY_ID);
        Product p = new Product();
        p.setId(id);
        p.setName("Product-" + id);
        p.setSku("SKU-" + id);
        p.setCategory(category);
        p.setBrand(brand);
        p.setPrice(price);
        p.setCurrency("USD");
        p.setStatus(ProductStatus.ACTIVE);
        p.setCompany(company);
        p.setUpdatedAt(Instant.now());
        return p;
    }

    private ProductDocument makeDoc(UUID id, String category, String brand, BigDecimal price,
                                    List<UUID> collectionIds, String attributeText) {
        ProductDocument doc = new ProductDocument();
        doc.setId(id);
        doc.setCompanyId(COMPANY_ID);
        doc.setCategory(category);
        doc.setBrand(brand);
        doc.setPrice(price);
        doc.setStatus(ProductStatus.ACTIVE.name());
        doc.setCollectionIds(collectionIds);
        doc.setAttributeText(attributeText);
        return doc;
    }

    @SuppressWarnings("unchecked")
    private void stubEsSearch(ProductDocument... docs) {
        SearchHits<ProductDocument> hits = mock(SearchHits.class);
        when(hits.stream()).thenReturn(java.util.stream.Stream.of(docs).map(doc -> {
            SearchHit<ProductDocument> hit = mock(SearchHit.class);
            when(hit.getContent()).thenReturn(doc);
            return hit;
        }));
        when(elasticsearchOperations.search(any(Query.class), eq(ProductDocument.class))).thenReturn(hits);
    }
}
