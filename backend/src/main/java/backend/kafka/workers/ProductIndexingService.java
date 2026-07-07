package backend.kafka.workers;

import backend.configurations.environment.EnvironmentSetting;
import backend.documents.BundleDocument;
import backend.documents.ProductDocument;
import backend.models.core.IndexingFailure;
import backend.models.core.Product;
import backend.models.core.ProductBundle;
import backend.models.core.PromotionRule;
import backend.models.enums.PromotionRuleType;
import backend.repositories.BundleRepository;
import backend.repositories.CollectionProductRepository;
import backend.repositories.IndexingFailureRepository;
import backend.repositories.ProductRepository;
import backend.repositories.PromotionRuleRepository;
import backend.repositories.search.BundleSearchRepository;
import backend.repositories.search.ProductSearchRepository;
import backend.services.pricing.config.FixedOffConfig;
import backend.services.pricing.config.PercentageOffConfig;
import backend.services.pricing.config.PromotionConfigValidator;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ProductIndexingService implements ApplicationRunner {

    private record FailurePair(UUID documentId, UUID companyId) {}

    private static final Logger log = LoggerFactory.getLogger(ProductIndexingService.class);

    private final ProductSearchRepository productSearchRepository;
    private final BundleSearchRepository bundleSearchRepository;
    private final ProductRepository productRepository;
    private final BundleRepository bundleRepository;
    private final PromotionRuleRepository promotionRuleRepository;
    private final CollectionProductRepository collectionProductRepository;
    private final backend.repositories.MarketplaceVendorRepository marketplaceVendorRepository;
    private final PromotionConfigValidator configValidator;
    private final IndexingFailureRepository failureRepository;
    private final IndexVersionManager indexVersionManager;
    private final EnvironmentSetting env;

    private volatile LinkedBlockingQueue<IndexingTask> taskQueue;
    private volatile ExecutorService workerPool;

    public ProductIndexingService(
            ProductSearchRepository productSearchRepository,
            BundleSearchRepository bundleSearchRepository,
            ProductRepository productRepository,
            BundleRepository bundleRepository,
            PromotionRuleRepository promotionRuleRepository,
            CollectionProductRepository collectionProductRepository,
            backend.repositories.MarketplaceVendorRepository marketplaceVendorRepository,
            PromotionConfigValidator configValidator,
            IndexingFailureRepository failureRepository,
            IndexVersionManager indexVersionManager,
            EnvironmentSetting env) {
        this.productSearchRepository = productSearchRepository;
        this.bundleSearchRepository = bundleSearchRepository;
        this.productRepository = productRepository;
        this.bundleRepository = bundleRepository;
        this.promotionRuleRepository = promotionRuleRepository;
        this.collectionProductRepository = collectionProductRepository;
        this.marketplaceVendorRepository = marketplaceVendorRepository;
        this.configValidator = configValidator;
        this.failureRepository = failureRepository;
        this.indexVersionManager = indexVersionManager;
        this.env = env;
    }

    @PostConstruct
    void startWorkers() {
        EnvironmentSetting.Elasticsearch.Indexing cfg = env.getElasticsearch().getIndexing();
        taskQueue = new LinkedBlockingQueue<>(cfg.getQueueCapacity());
        AtomicInteger idx = new AtomicInteger(0);
        workerPool = Executors.newFixedThreadPool(cfg.getWorkerCount(), r -> {
            Thread t = new Thread(r, "search-worker-" + idx.getAndIncrement());
            t.setDaemon(true);
            return t;
        });
        for (int i = 0; i < cfg.getWorkerCount(); i++) {
            workerPool.submit(this::workerLoop);
        }
        log.info("[SEARCH INDEX] Started {} worker(s), queue={}, batch={}",
                cfg.getWorkerCount(), cfg.getQueueCapacity(), cfg.getBatchSize());
    }

    @PreDestroy
    void shutdown() {
        log.info("[SEARCH INDEX] Shutting down — pending tasks: {}", taskQueue.size());
        workerPool.shutdownNow();
        try {
            if (!workerPool.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("[SEARCH INDEX] Workers did not terminate within 5s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // -------------------------------------------------------------------------
    // Public API — called by Kafka consumers, startup reindex, and retry scheduler
    // -------------------------------------------------------------------------

    /**
     * @param companyId must be passed explicitly — product.getCompany().getId() resolved
     *                  while the entity is still loaded in the calling
     *                  JPA session (prevents lazy-load in workers)
     */
    public void indexProduct(Product product, UUID companyId) {
        submit(new IndexingTask.IndexProduct(product, companyId));
    }

    public void removeProduct(UUID productId) {
        submit(new IndexingTask.RemoveProduct(productId));
    }

    public void indexBundle(ProductBundle bundle) {
        submit(new IndexingTask.IndexBundle(bundle));
    }

    public void removeBundle(UUID bundleId) {
        submit(new IndexingTask.RemoveBundle(bundleId));
    }

    // -------------------------------------------------------------------------
    // Full reindex — queues all documents for a company or the entire catalog
    // -------------------------------------------------------------------------

    public void reindexCompany(UUID companyId) {
        productRepository.findAllByCompanyIdWithCompany(companyId)
                .forEach(p -> submit(new IndexingTask.IndexProduct(p, companyId)));
        bundleRepository.findAllByCompanyId(companyId)
                .forEach(b -> submit(new IndexingTask.IndexBundle(b)));
        log.info("[SEARCH INDEX] Full reindex triggered for company {}", companyId);
    }

    public void reindexAll() {
        productRepository.findAllWithCompany()
                .forEach(p -> submit(new IndexingTask.IndexProduct(p, p.getCompany().getId())));
        bundleRepository.findAll()
                .forEach(b -> submit(new IndexingTask.IndexBundle(b)));
        log.info("[SEARCH INDEX] Global full reindex triggered");
    }

    private void submit(IndexingTask task) {
        if (!taskQueue.offer(task)) {
            log.warn("[SEARCH INDEX] Queue full — dropping task: {}", task);
        }
    }

    // -------------------------------------------------------------------------
    // Startup reindex — non-blocking: queues all entities, workers drain async
    // -------------------------------------------------------------------------

    @Override
    public void run(ApplicationArguments args) {
        indexVersionManager.ensureIndexExists("products");
        indexVersionManager.ensureIndexExists("bundles");

        try {
            if (productSearchRepository.count() == 0) {
                productRepository.findAllWithCompany()
                        .forEach(p -> submit(new IndexingTask.IndexProduct(p, p.getCompany().getId())));
                log.info("[SEARCH INDEX] Queued existing products for initial indexing");
            }
        } catch (Exception e) {
            log.warn("[SEARCH INDEX] Product startup reindex skipped: {}", e.getMessage());
        }

        try {
            if (bundleSearchRepository.count() == 0) {
                bundleRepository.findAll()
                        .forEach(b -> submit(new IndexingTask.IndexBundle(b)));
                log.info("[SEARCH INDEX] Queued existing bundles for initial indexing");
            }
        } catch (Exception e) {
            log.warn("[SEARCH INDEX] Bundle startup reindex skipped: {}", e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Worker internals
    // -------------------------------------------------------------------------

    private void workerLoop() {
        int batchSize = env.getElasticsearch().getIndexing().getBatchSize();
        while (!Thread.currentThread().isInterrupted()) {
            try {
                IndexingTask first = taskQueue.poll(200, TimeUnit.MILLISECONDS);
                if (first == null) continue;
                List<IndexingTask> batch = new ArrayList<>(batchSize);
                batch.add(first);
                taskQueue.drainTo(batch, batchSize - 1);
                processBatch(batch);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.warn("[SEARCH INDEX] Worker error: {}", e.getMessage());
            }
        }
        // Drain remaining tasks on shutdown
        List<IndexingTask> remaining = new ArrayList<>();
        taskQueue.drainTo(remaining);
        if (!remaining.isEmpty()) {
            try {
                processBatch(remaining);
            } catch (Exception e) {
                log.warn("[SEARCH INDEX] Shutdown drain error: {}", e.getMessage());
            }
        }
    }

    private void processBatch(List<IndexingTask> batch) {
        List<ProductDocument> toIndex         = new ArrayList<>();
        List<UUID>            toRemove        = new ArrayList<>();
        List<BundleDocument>  bundlesToIndex  = new ArrayList<>();
        List<UUID>            bundlesToRemove = new ArrayList<>();

        for (IndexingTask task : batch) {
            switch (task) {
                case IndexingTask.IndexProduct  t -> toIndex.add(toProductDocument(t.product(), t.companyId()));
                case IndexingTask.RemoveProduct t -> toRemove.add(t.productId());
                case IndexingTask.IndexBundle   t -> bundlesToIndex.add(toBundleDocument(t.bundle()));
                case IndexingTask.RemoveBundle  t -> bundlesToRemove.add(t.bundleId());
            }
        }

        if (!toIndex.isEmpty()) {
            try { productSearchRepository.saveAll(toIndex); }
            catch (Exception e) {
                log.warn("[SEARCH INDEX] saveAll products failed: {}", e.getMessage());
                persistFailures("PRODUCT", "INDEX",
                        toIndex.stream().map(d -> new FailurePair(d.getId(), d.getCompanyId())).toList(),
                        e.getMessage());
            }
        }
        if (!toRemove.isEmpty()) {
            try { productSearchRepository.deleteAllById(toRemove); }
            catch (Exception e) {
                log.warn("[SEARCH INDEX] deleteAll products failed: {}", e.getMessage());
                persistRemoveFailures("PRODUCT", toRemove, e.getMessage());
            }
        }
        if (!bundlesToIndex.isEmpty()) {
            try { bundleSearchRepository.saveAll(bundlesToIndex); }
            catch (Exception e) {
                log.warn("[SEARCH INDEX] saveAll bundles failed: {}", e.getMessage());
                persistFailures("BUNDLE", "INDEX",
                        bundlesToIndex.stream().map(d -> new FailurePair(d.getId(), d.getCompanyId())).toList(),
                        e.getMessage());
            }
        }
        if (!bundlesToRemove.isEmpty()) {
            try { bundleSearchRepository.deleteAllById(bundlesToRemove); }
            catch (Exception e) {
                log.warn("[SEARCH INDEX] deleteAll bundles failed: {}", e.getMessage());
                persistRemoveFailures("BUNDLE", bundlesToRemove, e.getMessage());
            }
        }
    }

    private void persistFailures(String docType, String operation, List<FailurePair> idPairs, String errorMessage) {
        try {
            for (FailurePair pair : idPairs) {
                IndexingFailure f = new IndexingFailure();
                f.setDocumentType(docType);
                f.setDocumentId(pair.documentId());
                f.setCompanyId(pair.companyId());
                f.setOperation(operation);
                f.setErrorMessage(truncate(errorMessage, 500));
                failureRepository.save(f);
            }
        } catch (Exception ex) {
            log.error("[SEARCH INDEX] Failed to persist DLQ record: {}", ex.getMessage());
        }
    }

    private void persistRemoveFailures(String docType, List<UUID> ids, String errorMessage) {
        try {
            for (UUID id : ids) {
                IndexingFailure f = new IndexingFailure();
                f.setDocumentType(docType);
                f.setDocumentId(id);
                f.setOperation("REMOVE");
                f.setErrorMessage(truncate(errorMessage, 500));
                failureRepository.save(f);
            }
        } catch (Exception ex) {
            log.error("[SEARCH INDEX] Failed to persist DLQ record: {}", ex.getMessage());
        }
    }

    private static String truncate(String s, int max) {
        return s != null && s.length() > max ? s.substring(0, max) : s;
    }

    // -------------------------------------------------------------------------
    // Document builders
    // -------------------------------------------------------------------------

    private ProductDocument toProductDocument(Product p, UUID companyId) {
        Instant now = Instant.now();
        List<PromotionRule> rules = promotionRuleRepository.findActiveRulesForProduct(companyId, p.getId(), now);

        List<String> discountCategories = rules.stream()
                .map(PromotionRule::getDescription)
                .filter(Objects::nonNull)
                .map(String::toLowerCase)
                .distinct()
                .toList();

        BigDecimal bestSaving = rules.stream()
                .map(r -> computeSaving(r, p.getPrice()))
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        boolean hasActiveDiscount = bestSaving.compareTo(BigDecimal.ZERO) > 0;
        BigDecimal discountedPrice = hasActiveDiscount ? p.getPrice().subtract(bestSaving) : null;

        // vendorName: safe access — may not be loaded in async worker threads
        String vendorName = null;
        try { vendorName = p.getCompany().getName(); } catch (Exception ignored) {}

        // Resolve the MarketplaceVendor.id for marketplace products so the indexed vendorId matches
        // the id the public catalog API exposes and filters on (NOT the owning company id).
        UUID marketplaceVendorId = null;
        if (p.getMarketplaceId() != null) {
            try {
                marketplaceVendorId = marketplaceVendorRepository
                        .findByMarketplaceIdAndVendorCompanyId(p.getMarketplaceId(), companyId)
                        .map(mv -> mv.getId())
                        .orElse(null);
            } catch (Exception ignored) {}
        }

        String nameCompletion = p.getName() != null
                ? p.getName() + (p.getBrand() != null ? " " + p.getBrand() : "")
                : null;

        // Active collection ids — loaded once per indexing call. Empty when the product isn't
        // a member of any collection; null when the lookup fails (Redis-style: don't propagate).
        List<UUID> collectionIds = null;
        try {
            collectionIds = collectionProductRepository.findProductCollectionPairs(List.of(p.getId()))
                    .stream()
                    .map(row -> (UUID) row[1])
                    .distinct()
                    .toList();
            if (collectionIds.isEmpty()) collectionIds = null;
        } catch (Exception e) {
            log.warn("[SEARCH INDEX] Failed to load collection memberships for product {}: {}",
                    p.getId(), e.getMessage());
        }

        // Pin: only carry forward when still in window. ES will treat the absent date as "no pin".
        // pinnedRank is dropped alongside the date so that "sort by pinnedRank asc, missing last"
        // correctly buckets expired pins with the rest of the catalog.
        Instant pinnedUntil = p.getPinnedUntil();
        Integer pinnedRank = p.getPinnedRank();
        if (pinnedUntil == null || !pinnedUntil.isAfter(now)) {
            pinnedUntil = null;
            pinnedRank = null;
        }

        // Attributes are a lazy collection and this runs on an async worker thread with no open
        // session — guard the access like vendorName / collectionIds above so an incremental
        // re-index (whose fetch query doesn't JOIN FETCH attributes) doesn't crash the whole batch.
        String attributeText = null;
        try {
            if (p.getAttributes() != null && !p.getAttributes().isEmpty()) {
                attributeText = p.getAttributes().stream()
                        .filter(a -> a.getName() != null && a.getValue() != null)
                        .map(a -> a.getName() + ":" + a.getValue())
                        .collect(java.util.stream.Collectors.joining(" "));
            }
        } catch (Exception e) {
            log.warn("[SEARCH INDEX] Failed to load attributes for product {}: {}", p.getId(), e.getMessage());
        }

        return new ProductDocument(
                p.getId(),
                companyId,
                p.getMarketplaceId(),
                marketplaceVendorId,   // vendorId = MarketplaceVendor.id (matches catalog API/DTO)
                companyId,             // vendorCompanyId = owning company id
                vendorName,
                p.isMarketplaceListed(),
                p.getName(),
                p.getDescription(),
                p.getSku(),
                p.getCategory(),
                p.getBrand(),
                p.getTags(),
                nameCompletion,
                p.getStatus() != null ? p.getStatus().name() : null,
                p.getScheduledPublishAt(),
                p.getPublishedAt(),
                p.isFeatured(),
                p.isListed(),
                p.getThumbnailUrl(),
                p.getPrice(),
                discountCategories.isEmpty() ? null : discountCategories,
                hasActiveDiscount,
                discountedPrice,
                p.getBoostWeight(),
                pinnedUntil,
                pinnedRank,
                collectionIds,
                attributeText,
                p.getCreatedAt()
        );
    }

    /**
     * Quick per-unit saving for indexing. Only PERCENTAGE_OFF and FIXED_OFF contribute —
     * BOGO/TIERED/FREE_SHIPPING depend on quantity or apply to shipping, so they don't
     * produce a stable per-unit discounted price for search results.
     */
    private BigDecimal computeSaving(PromotionRule rule, BigDecimal price) {
        if (rule.getRuleType() == PromotionRuleType.PERCENTAGE_OFF) {
            PercentageOffConfig cfg = (PercentageOffConfig)
                    configValidator.parseStored(rule.getRuleType(), rule.getConfigJson());
            BigDecimal saving = price.multiply(cfg.percent())
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            if (cfg.maxDiscount() != null) {
                saving = saving.min(cfg.maxDiscount());
            }
            return saving.min(price);
        }
        if (rule.getRuleType() == PromotionRuleType.FIXED_OFF) {
            FixedOffConfig cfg = (FixedOffConfig)
                    configValidator.parseStored(rule.getRuleType(), rule.getConfigJson());
            return cfg.amount().min(price);
        }
        return BigDecimal.ZERO;
    }

    private BundleDocument toBundleDocument(ProductBundle b) {
        return new BundleDocument(
                b.getId(),
                b.getCompany().getId(),
                b.getName(),
                b.getDescription(),
                b.getStatus() != null ? b.getStatus().name() : null,
                b.isListed(),
                b.getPrice()
        );
    }
}
