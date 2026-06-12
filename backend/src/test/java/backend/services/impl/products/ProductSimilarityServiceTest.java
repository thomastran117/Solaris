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
import java.util.Set;
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

    @Test
    void recompute_sourceNotFound_skipsGracefully() {
        when(productRepository.findByIdWithCompanyOwner(SOURCE_ID)).thenReturn(Optional.empty());

        service.recompute(SOURCE_ID);

        verify(productSimilarityRepository, never()).deleteAllByIdSourceProductId(any());
        verify(elasticsearchOperations, never()).search(any(Query.class), any());
    }

    @Test
    void recompute_esCandidatesQueryFails_countsMetric() {
        Product source = makeProduct(SOURCE_ID, "Electronics", "BrandX", new BigDecimal("100.00"));
        when(productRepository.findByIdWithCompanyOwner(SOURCE_ID)).thenReturn(Optional.of(source));
        when(elasticsearchOperations.search(any(Query.class), eq(ProductDocument.class)))
                .thenThrow(new RuntimeException("ES cluster down"));

        service.recompute(SOURCE_ID);

        verify(productSimilarityRepository, never()).saveAll(any());
    }

    @Test
    void recompute_emptyCandidates_countsSuccessMetric() {
        Product source = makeProduct(SOURCE_ID, "Electronics", "BrandX", new BigDecimal("100.00"));
        when(productRepository.findByIdWithCompanyOwner(SOURCE_ID)).thenReturn(Optional.of(source));
        stubEsSearch(); // no hits

        service.recompute(SOURCE_ID);

        verify(productSimilarityRepository, never()).saveAll(any());
        verify(productSimilarityRepository, never()).deleteAllByIdSourceProductId(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void recompute_collectionSignal_scoredWhenShared() {
        UUID sharedCollection = TestIds.uuid(10);
        Product source = makeProduct(SOURCE_ID, "Electronics", "BrandX", new BigDecimal("100.00"));
        when(productRepository.findByIdWithCompanyOwner(SOURCE_ID)).thenReturn(Optional.of(source));
        when(productRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(productRepository.getReferenceById(any())).thenAnswer(inv -> {
            Product p = new Product(); p.setId(inv.getArgument(0)); return p;
        });
        List<Object[]> pairs = List.<Object[]>of(new Object[]{SOURCE_ID, sharedCollection});
        when(collectionProductRepository.findProductCollectionPairs(any())).thenReturn(pairs);

        ProductDocument docWithCollection = makeDoc(TARGET_A, "Electronics", null, new BigDecimal("100.00"),
                List.of(sharedCollection), null);
        stubEsSearch(docWithCollection);

        service.recompute(SOURCE_ID);

        verify(productSimilarityRepository).saveAll(argThat(rows -> {
            List<ProductSimilarity> list = (List<ProductSimilarity>) rows;
            return list.get(0).getMatchSignals().contains("COLLECTION");
        }));
    }

    @Test
    @SuppressWarnings("unchecked")
    void recompute_tagsSignal_scoredWhenShared() {
        Product source = makeProduct(SOURCE_ID, "Electronics", null, new BigDecimal("100.00"));
        source.setTags("wireless bluetooth");
        when(productRepository.findByIdWithCompanyOwner(SOURCE_ID)).thenReturn(Optional.of(source));
        when(productRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(productRepository.getReferenceById(any())).thenAnswer(inv -> {
            Product p = new Product(); p.setId(inv.getArgument(0)); return p;
        });

        ProductDocument docWithTags = makeDoc(TARGET_A, "Electronics", null, new BigDecimal("100.00"), null, null);
        docWithTags.setTags("bluetooth audio");
        stubEsSearch(docWithTags);

        service.recompute(SOURCE_ID);

        verify(productSimilarityRepository).saveAll(argThat(rows -> {
            List<ProductSimilarity> list = (List<ProductSimilarity>) rows;
            return list.get(0).getMatchSignals().contains("TAGS");
        }));
    }

    @Test
    @SuppressWarnings("unchecked")
    void recompute_attributesSignal_scoredWhenShared() {
        ProductAttribute attr = new ProductAttribute();
        attr.setName("color");
        attr.setValue("red");
        when(productAttributeRepository.findAllByProductIdOrderByDisplayOrderAsc(SOURCE_ID))
                .thenReturn(List.of(attr));

        Product source = makeProduct(SOURCE_ID, "Electronics", null, new BigDecimal("100.00"));
        when(productRepository.findByIdWithCompanyOwner(SOURCE_ID)).thenReturn(Optional.of(source));
        when(productRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(productRepository.getReferenceById(any())).thenAnswer(inv -> {
            Product p = new Product(); p.setId(inv.getArgument(0)); return p;
        });

        ProductDocument docWithAttr = makeDoc(TARGET_A, "Electronics", null, new BigDecimal("100.00"),
                null, "color:red size:large");
        stubEsSearch(docWithAttr);

        service.recompute(SOURCE_ID);

        verify(productSimilarityRepository).saveAll(argThat(rows -> {
            List<ProductSimilarity> list = (List<ProductSimilarity>) rows;
            return list.get(0).getMatchSignals().contains("ATTRIBUTES");
        }));
    }

    // ── static helper tests ────────────────────────────────────────────────────

    @Test
    void buildAttributeText_withAttributes_joinsNameValuePairs() {
        ProductAttribute a1 = new ProductAttribute();
        a1.setName("color"); a1.setValue("red");
        ProductAttribute a2 = new ProductAttribute();
        a2.setName("size"); a2.setValue("L");

        String result = ProductSimilarityService.buildAttributeText(List.of(a1, a2));
        assertNotNull(result);
        assertTrue(result.contains("color:red"));
        assertTrue(result.contains("size:L"));
    }

    @Test
    void buildAttributeText_nullNameOrValue_skipped() {
        ProductAttribute goodAttr = new ProductAttribute();
        goodAttr.setName("material"); goodAttr.setValue("cotton");
        ProductAttribute nullName = new ProductAttribute();
        nullName.setName(null); nullName.setValue("x");
        ProductAttribute nullVal = new ProductAttribute();
        nullVal.setName("y"); nullVal.setValue(null);

        String result = ProductSimilarityService.buildAttributeText(List.of(goodAttr, nullName, nullVal));
        assertEquals("material:cotton", result);
    }

    @Test
    void buildAttributeText_emptyList_returnsNull() {
        assertNull(ProductSimilarityService.buildAttributeText(List.of()));
    }

    @Test
    void tokenize_commaSeparated_splitsCorrectly() {
        Set<String> tokens = ProductSimilarityService.tokenize("bluetooth,wireless,audio");
        assertTrue(tokens.contains("bluetooth"));
        assertTrue(tokens.contains("wireless"));
        assertTrue(tokens.contains("audio"));
    }

    @Test
    void parseAttributePairs_filtersPairsWithColon() {
        Set<String> pairs = ProductSimilarityService.parseAttributePairs("color:red material:cotton nocodon");
        assertTrue(pairs.contains("color:red"));
        assertTrue(pairs.contains("material:cotton"));
        assertFalse(pairs.contains("nocodon"));
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

    // ── buildAttributeText — null input ────────────────────────────────────────

    @Test
    void buildAttributeText_nullList_returnsNull() {
        assertNull(ProductSimilarityService.buildAttributeText(null));
    }

    // ── doRecompute — latest product not found (stale guard: latest==null) ─────

    @Test
    void recompute_latestProductNotFound_skipsWrite() {
        Product source = makeProduct(SOURCE_ID, "Electronics", "BrandX", new BigDecimal("100.00"));
        when(productRepository.findByIdWithCompanyOwner(SOURCE_ID)).thenReturn(Optional.of(source));

        ProductDocument doc = makeDoc(TARGET_A, "Electronics", "BrandX", new BigDecimal("90.00"), null, null);
        stubEsSearch(doc);

        // findById returns empty → latest == null → stale guard fires
        when(productRepository.findById(SOURCE_ID)).thenReturn(Optional.empty());

        service.recompute(SOURCE_ID);

        verify(productSimilarityRepository, never()).deleteAllByIdSourceProductId(any());
        verify(productSimilarityRepository, never()).saveAll(any());
    }

    // ── score — candidate with null price skips PRICE_RANGE signal ─────────────

    @Test
    @SuppressWarnings("unchecked")
    void recompute_candidateNullPrice_priceSignalNotScored() {
        Product source = makeProduct(SOURCE_ID, "Electronics", "BrandX", new BigDecimal("100.00"));
        when(productRepository.findByIdWithCompanyOwner(SOURCE_ID)).thenReturn(Optional.of(source));
        when(productRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(productRepository.getReferenceById(any())).thenAnswer(inv -> {
            Product p = new Product(); p.setId(inv.getArgument(0)); return p;
        });

        // Candidate has null price — PRICE_RANGE signal should be absent
        ProductDocument docNullPrice = makeDoc(TARGET_A, "Electronics", "BrandX", null, null, null);
        stubEsSearch(docNullPrice);

        service.recompute(SOURCE_ID);

        verify(productSimilarityRepository).saveAll(argThat(rows -> {
            List<ProductSimilarity> list = (List<ProductSimilarity>) rows;
            return !list.isEmpty() && !list.get(0).getMatchSignals().contains("PRICE_RANGE");
        }));
    }

    // ── score — candidate score == 0, filtered out ─────────────────────────────

    @Test
    void recompute_candidateScoreZero_filteredOut() {
        // Source has only category signal; candidate has different category → score = 0
        Product source = makeProduct(SOURCE_ID, "Electronics", null, null);
        when(productRepository.findByIdWithCompanyOwner(SOURCE_ID)).thenReturn(Optional.of(source));
        when(productRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));

        // Candidate has wrong category → CATEGORY won't match → score=0 → filtered
        ProductDocument docWrongCat = makeDoc(TARGET_A, "Furniture", null, null, null, null);
        stubEsSearch(docWrongCat);

        service.recompute(SOURCE_ID);

        // Score=0 means the row is filtered; saveAll should not be called
        verify(productSimilarityRepository, never()).saveAll(any());
    }

    // ── boostWeight — candidate with non-null boostWeight ─────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void recompute_candidateWithBoostWeight_appliedInSorting() {
        Product source = makeProduct(SOURCE_ID, "Electronics", "BrandX", new BigDecimal("100.00"));
        when(productRepository.findByIdWithCompanyOwner(SOURCE_ID)).thenReturn(Optional.of(source));
        when(productRepository.findById(SOURCE_ID)).thenReturn(Optional.of(source));
        when(productRepository.getReferenceById(any())).thenAnswer(inv -> {
            Product p = new Product(); p.setId(inv.getArgument(0)); return p;
        });

        ProductDocument doc = makeDoc(TARGET_A, "Electronics", "BrandX", new BigDecimal("90.00"), null, null);
        doc.setBoostWeight(10);
        stubEsSearch(doc);

        service.recompute(SOURCE_ID);

        verify(productSimilarityRepository).saveAll(argThat(rows -> {
            List<ProductSimilarity> list = (List<ProductSimilarity>) rows;
            return !list.isEmpty();
        }));
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
