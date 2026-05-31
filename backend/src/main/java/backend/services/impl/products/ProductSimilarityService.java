package backend.services.impl.products;

import backend.documents.ProductDocument;
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
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.IdsQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQuery;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ProductSimilarityService {

    private static final Logger log = LoggerFactory.getLogger(ProductSimilarityService.class);

    private static final int CANDIDATE_FETCH_SIZE = 25;
    private static final int MAX_STORED_ROWS = 10;
    private static final int MAX_COLLECTION_SIGNALS = 5;

    static final float W_CATEGORY   = 3.0f;
    static final float W_BRAND      = 2.0f;
    static final float W_COLLECTION = 2.0f;
    static final float W_PRICE_RANGE = 1.0f;
    static final float W_TAGS       = 1.5f;
    static final float W_ATTRIBUTES = 1.0f;

    private final ProductRepository productRepository;
    private final ProductAttributeRepository productAttributeRepository;
    private final CollectionProductRepository collectionProductRepository;
    private final ProductSimilarityRepository productSimilarityRepository;
    private final ElasticsearchOperations elasticsearchOperations;
    private final MeterRegistry meterRegistry;

    public ProductSimilarityService(
            ProductRepository productRepository,
            ProductAttributeRepository productAttributeRepository,
            CollectionProductRepository collectionProductRepository,
            ProductSimilarityRepository productSimilarityRepository,
            ElasticsearchOperations elasticsearchOperations,
            MeterRegistry meterRegistry) {
        this.productRepository = productRepository;
        this.productAttributeRepository = productAttributeRepository;
        this.collectionProductRepository = collectionProductRepository;
        this.productSimilarityRepository = productSimilarityRepository;
        this.elasticsearchOperations = elasticsearchOperations;
        this.meterRegistry = meterRegistry;
    }

    // ── Public entry point ──────────────────────────────────────────────────────

    public void recompute(UUID sourceProductId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            doRecompute(sourceProductId);
        } finally {
            sample.stop(Timer.builder("similarity_recompute_duration_ms").register(meterRegistry));
        }
    }

    // ── Core computation ────────────────────────────────────────────────────────

    private void doRecompute(UUID sourceProductId) {
        Product source = productRepository.findByIdWithCompanyOwner(sourceProductId).orElse(null);
        if (source == null) {
            log.warn("[Similarity] Source product {} not found, skipping recompute", sourceProductId);
            return;
        }

        Instant snapshotUpdatedAt = source.getUpdatedAt();
        UUID companyId = source.getCompany().getId();

        // Load source's collection IDs (sorted, capped for determinism)
        List<UUID> sourceCollectionIds = collectionProductRepository
                .findProductCollectionPairs(List.of(sourceProductId))
                .stream()
                .map(row -> (UUID) row[1])
                .distinct()
                .sorted()
                .limit(MAX_COLLECTION_SIGNALS)
                .toList();

        // Load source's attributes for text and attribute signal evaluation
        List<ProductAttribute> sourceAttributes = productAttributeRepository
                .findAllByProductIdOrderByDisplayOrderAsc(sourceProductId);
        String sourceAttributeText = buildAttributeText(sourceAttributes);

        // Available-max weight normalization: only count signals the source CAN provide
        float availableMaxWeight = computeAvailableMaxWeight(source, sourceCollectionIds, sourceAttributeText);
        if (availableMaxWeight == 0f) {
            log.debug("[Similarity] No signals available for product {}, skipping", sourceProductId);
            return;
        }

        // Query ES for candidates
        List<ProductDocument> candidates;
        try {
            candidates = queryCandidates(source, sourceCollectionIds, sourceAttributeText, companyId);
        } catch (Exception e) {
            log.warn("[Similarity] ES query failed for product {}: {}", sourceProductId, e.getMessage());
            meterRegistry.counter("similarity_recompute_failed_total").increment();
            return;
        }

        if (candidates.isEmpty()) {
            meterRegistry.counter("similarity_recompute_success_total").increment();
            return;
        }

        // Score and rank candidates
        List<ScoredRow> scoredRows = scoreAndRank(
                source, sourceCollectionIds, sourceAttributeText, availableMaxWeight, candidates);

        // Stale-write guard: abort if product was updated during computation
        Product latest = productRepository.findById(sourceProductId).orElse(null);
        if (latest == null || !Objects.equals(latest.getUpdatedAt(), snapshotUpdatedAt)) {
            log.debug("[Similarity] Skipping stale recompute for {} — newer event pending", sourceProductId);
            return;
        }

        replaceSimilarities(sourceProductId, scoredRows);

        meterRegistry.counter("similarity_recompute_success_total").increment();
        meterRegistry.counter("similarity_rows_written_total").increment(scoredRows.size());
    }

    @Transactional
    void replaceSimilarities(UUID sourceProductId, List<ScoredRow> scoredRows) {
        productSimilarityRepository.deleteAllByIdSourceProductId(sourceProductId);

        Product sourceRef = productRepository.getReferenceById(sourceProductId);
        List<ProductSimilarity> rows = scoredRows.stream().map(sr -> {
            Product targetRef = productRepository.getReferenceById(sr.targetId());
            ProductSimilarity sim = new ProductSimilarity();
            sim.setId(new ProductSimilarityId(sourceProductId, sr.targetId()));
            sim.setSourceProduct(sourceRef);
            sim.setTargetProduct(targetRef);
            sim.setScore(sr.score());
            sim.setMatchSignals(sr.matchSignals());
            sim.setComputedAt(Instant.now());
            return sim;
        }).toList();

        productSimilarityRepository.saveAll(rows);
    }

    // ── ES candidate retrieval ──────────────────────────────────────────────────

    private List<ProductDocument> queryCandidates(
            Product source, List<UUID> sourceCollectionIds, String sourceAttributeText, UUID companyId) {

        BoolQuery.Builder bq = new BoolQuery.Builder()
                .filter(TermQuery.of(t -> t.field("companyId").value(companyId.toString()))._toQuery())
                .filter(TermQuery.of(t -> t.field("status").value(ProductStatus.ACTIVE.name()))._toQuery())
                .mustNot(IdsQuery.of(ids -> ids.values(List.of(source.getId().toString())))._toQuery());

        // Hard category filter — only relevant when source has a category
        if (source.getCategory() != null && !source.getCategory().isBlank()) {
            final String cat = source.getCategory();
            bq.filter(TermQuery.of(t -> t.field("category").value(cat))._toQuery());
        }

        // Should: brand (candidate retrieval hint)
        if (source.getBrand() != null && !source.getBrand().isBlank()) {
            final String brand = source.getBrand();
            bq.should(TermQuery.of(t -> t.field("brand").value(brand))._toQuery());
        }

        // Should: price proximity
        if (source.getPrice() != null && source.getPrice().compareTo(BigDecimal.ZERO) > 0) {
            final double minP = source.getPrice().multiply(new BigDecimal("0.5")).doubleValue();
            final double maxP = source.getPrice().multiply(new BigDecimal("2.0")).doubleValue();
            bq.should(RangeQuery.of(r -> r.number(n -> n.field("price").gte(minP).lte(maxP)))._toQuery());
        }

        // Should: shared collections (any of source's first 5)
        if (!sourceCollectionIds.isEmpty()) {
            List<FieldValue> collectionValues = sourceCollectionIds.stream()
                    .map(id -> FieldValue.of(id.toString()))
                    .toList();
            bq.should(TermsQuery.of(t -> t.field("collectionIds")
                    .terms(tv -> tv.value(collectionValues)))._toQuery());
        }

        // Should: text overlap across name, tags, attributeText
        StringBuilder text = new StringBuilder();
        if (source.getName() != null) text.append(source.getName()).append(" ");
        if (source.getTags() != null) text.append(source.getTags()).append(" ");
        if (sourceAttributeText != null) text.append(sourceAttributeText);
        String textQuery = text.toString().trim();
        if (!textQuery.isBlank()) {
            bq.should(MultiMatchQuery.of(mm -> mm
                    .fields(List.of("name", "tags", "attributeText"))
                    .query(textQuery))._toQuery());
        }

        NativeQuery esQuery = NativeQuery.builder()
                .withQuery(bq.build()._toQuery())
                .withPageable(PageRequest.of(0, CANDIDATE_FETCH_SIZE))
                .build();

        return elasticsearchOperations.search(esQuery, ProductDocument.class)
                .stream()
                .map(h -> h.getContent())
                .toList();
    }

    // ── Signal scoring ──────────────────────────────────────────────────────────

    private List<ScoredRow> scoreAndRank(
            Product source,
            List<UUID> sourceCollectionIds,
            String sourceAttributeText,
            float availableMaxWeight,
            List<ProductDocument> candidates) {

        Set<UUID> sourceCollectionSet = new HashSet<>(sourceCollectionIds);
        Set<String> sourceTags = tokenize(source.getTags());
        Set<String> sourceAttrPairs = parseAttributePairs(sourceAttributeText);

        return candidates.stream()
                .map(c -> score(source, sourceTags, sourceAttrPairs, sourceCollectionSet, availableMaxWeight, c))
                .filter(sr -> sr.score() > 0f)
                .sorted(Comparator.comparingDouble(ScoredRow::score).reversed()
                        .thenComparingInt(sr -> -(sr.boostWeight())))
                .limit(MAX_STORED_ROWS)
                .toList();
    }

    private ScoredRow score(
            Product source,
            Set<String> sourceTags,
            Set<String> sourceAttrPairs,
            Set<UUID> sourceCollectionSet,
            float availableMaxWeight,
            ProductDocument candidate) {

        List<SimilaritySignal> matched = new ArrayList<>();
        float weight = 0f;

        if (source.getCategory() != null && !source.getCategory().isBlank()
                && source.getCategory().equals(candidate.getCategory())) {
            matched.add(SimilaritySignal.CATEGORY);
            weight += W_CATEGORY;
        }

        if (source.getBrand() != null && !source.getBrand().isBlank()
                && source.getBrand().equals(candidate.getBrand())) {
            matched.add(SimilaritySignal.BRAND);
            weight += W_BRAND;
        }

        if (!sourceCollectionSet.isEmpty() && candidate.getCollectionIds() != null
                && candidate.getCollectionIds().stream().anyMatch(sourceCollectionSet::contains)) {
            matched.add(SimilaritySignal.COLLECTION);
            weight += W_COLLECTION;
        }

        if (source.getPrice() != null && source.getPrice().compareTo(BigDecimal.ZERO) > 0
                && candidate.getPrice() != null) {
            BigDecimal min = source.getPrice().multiply(new BigDecimal("0.5"));
            BigDecimal max = source.getPrice().multiply(new BigDecimal("2.0"));
            if (candidate.getPrice().compareTo(min) >= 0 && candidate.getPrice().compareTo(max) <= 0) {
                matched.add(SimilaritySignal.PRICE_RANGE);
                weight += W_PRICE_RANGE;
            }
        }

        if (!sourceTags.isEmpty() && candidate.getTags() != null && !candidate.getTags().isBlank()) {
            Set<String> candidateTags = tokenize(candidate.getTags());
            if (candidateTags.stream().anyMatch(sourceTags::contains)) {
                matched.add(SimilaritySignal.TAGS);
                weight += W_TAGS;
            }
        }

        if (!sourceAttrPairs.isEmpty()) {
            Set<String> candidateAttrPairs = parseAttributePairs(candidate.getAttributeText());
            if (candidateAttrPairs.stream().anyMatch(sourceAttrPairs::contains)) {
                matched.add(SimilaritySignal.ATTRIBUTES);
                weight += W_ATTRIBUTES;
            }
        }

        float normalizedScore = weight / availableMaxWeight;
        // Sorted alphabetically (enum constants are already in alphabetical order)
        String matchSignals = matched.stream().sorted().map(Enum::name).collect(Collectors.joining(","));
        int boostWeight = candidate.getBoostWeight() != null ? candidate.getBoostWeight() : 0;

        return new ScoredRow(candidate.getId(), normalizedScore, matchSignals, boostWeight);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private float computeAvailableMaxWeight(Product source, List<UUID> collectionIds, String attributeText) {
        float max = 0f;
        if (source.getCategory() != null && !source.getCategory().isBlank()) max += W_CATEGORY;
        if (source.getBrand() != null && !source.getBrand().isBlank()) max += W_BRAND;
        if (!collectionIds.isEmpty()) max += W_COLLECTION;
        if (source.getPrice() != null && source.getPrice().compareTo(BigDecimal.ZERO) > 0) max += W_PRICE_RANGE;
        if (source.getTags() != null && !source.getTags().isBlank()) max += W_TAGS;
        if (attributeText != null && !attributeText.isBlank()) max += W_ATTRIBUTES;
        return max;
    }

    static String buildAttributeText(List<ProductAttribute> attributes) {
        if (attributes == null || attributes.isEmpty()) return null;
        String text = attributes.stream()
                .filter(a -> a.getName() != null && a.getValue() != null)
                .map(a -> a.getName() + ":" + a.getValue())
                .collect(Collectors.joining(" "));
        return text.isBlank() ? null : text;
    }

    static Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) return Set.of();
        return Arrays.stream(text.toLowerCase().split("[\\s,;]+"))
                .filter(t -> !t.isBlank())
                .collect(Collectors.toSet());
    }

    static Set<String> parseAttributePairs(String attributeText) {
        if (attributeText == null || attributeText.isBlank()) return Set.of();
        return Arrays.stream(attributeText.split("\\s+"))
                .filter(pair -> pair.contains(":"))
                .collect(Collectors.toSet());
    }

    /**
     * Parses a comma-separated matchSignals CSV (e.g. "CATEGORY,BRAND") into a list of
     * {@link SimilaritySignal}. Unknown or future enum values are silently dropped.
     */
    public static List<SimilaritySignal> parseSignals(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .flatMap(s -> {
                    try { return Stream.of(SimilaritySignal.valueOf(s)); }
                    catch (IllegalArgumentException e) { return Stream.empty(); }
                })
                .toList();
    }

    // ── Inner records ────────────────────────────────────────────────────────────

    record ScoredRow(UUID targetId, float score, String matchSignals, int boostWeight) {}
}
