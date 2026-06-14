package backend.services.impl.collections;

import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import backend.dtos.requests.collection.AddCollectionProductRequest;
import backend.dtos.requests.collection.CreateCollectionRequest;
import backend.dtos.requests.collection.UpdateCollectionProductRequest;
import backend.dtos.requests.collection.UpdateCollectionRequest;
import backend.dtos.responses.collection.CollectionProductResponse;
import backend.dtos.responses.collection.CollectionResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.product.ActivePromotionSummary;
import backend.events.ProductIndexEvent;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Collection;
import backend.models.core.CollectionProduct;
import backend.models.core.Company;
import backend.models.core.Product;
import backend.models.enums.CollectionMembershipSource;
import backend.models.enums.CollectionStatus;
import backend.models.enums.CollectionType;
import backend.models.enums.ProductStatus;
import backend.repositories.CollectionProductRepository;
import backend.repositories.CollectionRepository;
import backend.repositories.CompanyRepository;
import backend.repositories.MarketplaceProfileRepository;
import backend.repositories.ProductRepository;
import backend.repositories.specifications.ProductSpecification;
import backend.services.impl.SingleFlightCache;
import backend.services.impl.pricing.ActivePromotionLookupService;
import backend.services.intf.collections.CollectionService;
import backend.services.intf.company.CompanyAccessService;
import backend.models.enums.CompanyCapability;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CollectionServiceImpl implements CollectionService {

    private static final Logger log = LoggerFactory.getLogger(CollectionServiceImpl.class);

    private final CollectionRepository collectionRepository;
    private final CollectionProductRepository collectionProductRepository;
    private final ProductRepository productRepository;
    private final CompanyRepository companyRepository;
    private final MarketplaceProfileRepository marketplaceProfileRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final SingleFlightCache singleFlightCache;
    private final ActivePromotionLookupService activePromotionLookupService;
    private final ObjectMapper objectMapper;
    private final CompanyAccessService companyAccessService;

    public CollectionServiceImpl(
            CollectionRepository collectionRepository,
            CollectionProductRepository collectionProductRepository,
            ProductRepository productRepository,
            CompanyRepository companyRepository,
            MarketplaceProfileRepository marketplaceProfileRepository,
            ApplicationEventPublisher eventPublisher,
            SingleFlightCache singleFlightCache,
            ActivePromotionLookupService activePromotionLookupService,
            ObjectMapper objectMapper,
            CompanyAccessService companyAccessService) {
        this.collectionRepository = collectionRepository;
        this.collectionProductRepository = collectionProductRepository;
        this.productRepository = productRepository;
        this.companyRepository = companyRepository;
        this.marketplaceProfileRepository = marketplaceProfileRepository;
        this.eventPublisher = eventPublisher;
        this.singleFlightCache = singleFlightCache;
        this.activePromotionLookupService = activePromotionLookupService;
        this.objectMapper = objectMapper;
        this.companyAccessService = companyAccessService;
    }

    // -------------------------------------------------------------------------
    // Admin: collection CRUD
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<CollectionResponse> listCollections(
            UUID companyId, UUID ownerId, CollectionType type, CollectionStatus status, Boolean featured,
            int page, int size) {
        // Admin route: collections (incl. drafts and internal rulesJson) are private merchant data.
        // Public storefront browsing goes through GET /marketplaces/{id}/collections instead.
        companyAccessService.require(companyId, ownerId, CompanyCapability.READ_PRODUCTS);
        Pageable pageable = PageRequest.of(page, Math.min(size, 50),
                Sort.by(Sort.Direction.DESC, "updatedAt"));

        Page<Collection> collections;
        if (type != null) {
            collections = collectionRepository.findAllByCompanyIdAndType(companyId, type, pageable);
        } else if (status != null) {
            collections = collectionRepository.findAllByCompanyIdAndStatus(companyId, status, pageable);
        } else {
            collections = collectionRepository.findAllByCompanyId(companyId, pageable);
        }

        List<CollectionResponse> items = collections.getContent().stream()
                .filter(c -> featured == null || c.isFeatured() == featured)
                .map(this::toResponse)
                .toList();

        return new PagedResponse<>(new PageImpl<>(items, pageable, collections.getTotalElements()));
    }

    @Override
    @Transactional(readOnly = true)
    public CollectionResponse getCollection(UUID companyId, UUID collectionId, UUID ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.READ_PRODUCTS);
        Collection collection = collectionRepository.findByIdAndCompanyId(collectionId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Collection not found"));
        return toResponse(collection);
    }

    @Override
    @Transactional
    public CollectionResponse createCollection(UUID companyId, UUID ownerId, CreateCollectionRequest request) {
        Company company = companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PRODUCTS);

        String slug = normaliseSlug(request.getSlug());
        if (collectionRepository.existsBySlugAndCompanyId(slug, companyId)) {
            throw new ConflictException("A collection with this slug already exists");
        }

        if (request.getType() == CollectionType.DYNAMIC) {
            // Validate rule body early so the admin gets a clear error before the worker runs.
            DynamicCollectionRules.parse(request.getRulesJson(), objectMapper);
        }

        Collection collection = new Collection();
        collection.setCompany(company);
        collection.setName(request.getName());
        collection.setSlug(slug);
        collection.setDescription(request.getDescription());
        collection.setImageUrl(request.getImageUrl());
        collection.setType(request.getType() != null ? request.getType() : CollectionType.STATIC);
        collection.setStatus(request.getStatus() != null ? request.getStatus() : CollectionStatus.DRAFT);
        collection.setFeatured(request.isFeatured());
        collection.setFeaturedRank(request.getFeaturedRank());
        collection.setRulesJson(collection.getType() == CollectionType.DYNAMIC ? request.getRulesJson() : null);

        Collection saved = collectionRepository.save(collection);

        if (saved.getType() == CollectionType.DYNAMIC && saved.getStatus() == CollectionStatus.ACTIVE) {
            materialiseDynamicMembers(saved);
        }

        evictAfterCommit(() -> evictCollectionCaches(companyId, saved.getId(), saved.getSlug()));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public CollectionResponse updateCollection(UUID companyId, UUID collectionId, UUID ownerId,
                                               UpdateCollectionRequest request) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PRODUCTS);

        Collection collection = collectionRepository.findByIdAndCompanyId(collectionId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Collection not found"));

        if (request.getSlug() != null && !request.getSlug().isBlank()) {
            String newSlug = normaliseSlug(request.getSlug());
            if (!newSlug.equals(collection.getSlug())
                    && collectionRepository.existsBySlugAndCompanyId(newSlug, companyId)) {
                throw new ConflictException("A collection with this slug already exists");
            }
            collection.setSlug(newSlug);
        }
        if (request.getName() != null) collection.setName(request.getName());
        if (request.getDescription() != null) collection.setDescription(request.getDescription());
        if (request.getImageUrl() != null) collection.setImageUrl(request.getImageUrl());
        if (request.getStatus() != null) collection.setStatus(request.getStatus());
        if (request.getFeatured() != null) collection.setFeatured(request.getFeatured());
        if (request.getFeaturedRank() != null) collection.setFeaturedRank(request.getFeaturedRank());

        boolean wasDynamic = collection.getType() == CollectionType.DYNAMIC;
        boolean rulesChanged = request.getRulesJson() != null
                && !request.getRulesJson().equals(collection.getRulesJson());
        boolean typeChanged = request.getType() != null && request.getType() != collection.getType();

        if (typeChanged) {
            collection.setType(request.getType());
        }
        if (request.getRulesJson() != null) {
            collection.setRulesJson(request.getRulesJson());
        }
        if (collection.getType() == CollectionType.DYNAMIC) {
            DynamicCollectionRules.parse(collection.getRulesJson(), objectMapper);
        } else {
            collection.setRulesJson(null);
        }

        Collection saved = collectionRepository.save(collection);

        // Re-materialise when DYNAMIC and rules/type/status flipped to a refresh-relevant state.
        if (saved.getType() == CollectionType.DYNAMIC
                && saved.getStatus() == CollectionStatus.ACTIVE
                && (rulesChanged || typeChanged)) {
            materialiseDynamicMembers(saved);
        } else if (wasDynamic && saved.getType() == CollectionType.STATIC) {
            // Switching DYNAMIC → STATIC: strip the AUTO membership rows so the admin can curate by hand.
            collectionProductRepository.deleteByCollectionIdAndSource(
                    saved.getId(), CollectionMembershipSource.AUTO);
        }

        evictAfterCommit(() -> evictCollectionCaches(companyId, saved.getId(), saved.getSlug()));
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteCollection(UUID companyId, UUID collectionId, UUID ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PRODUCTS);

        Collection collection = collectionRepository.findByIdAndCompanyId(collectionId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Collection not found"));

        List<UUID> memberProductIds = collectionProductRepository.findAllByCollectionId(collectionId).stream()
                .map(cp -> cp.getProduct().getId())
                .toList();

        collectionProductRepository.deleteByCollectionIdAndSource(collectionId, CollectionMembershipSource.MANUAL);
        collectionProductRepository.deleteByCollectionIdAndSource(collectionId, CollectionMembershipSource.AUTO);
        collectionRepository.delete(collection);

        // Reindex affected products so the collectionIds list in ES drops the deleted id.
        publishReindex(companyId, memberProductIds);

        final String slug = collection.getSlug();
        evictAfterCommit(() -> evictCollectionCaches(companyId, collectionId, slug));
    }

    // -------------------------------------------------------------------------
    // Admin: collection ↔ product membership
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<CollectionProductResponse> listCollectionProducts(
            UUID companyId, UUID collectionId, UUID ownerId, int page, int size) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.READ_PRODUCTS);
        Collection collection = collectionRepository.findByIdAndCompanyId(collectionId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Collection not found"));

        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<CollectionProduct> rows = collectionProductRepository
                .findAllByCollectionIdRanked(collection.getId(), pageable);

        List<Product> products = rows.getContent().stream().map(CollectionProduct::getProduct).toList();
        Map<UUID, ActivePromotionSummary> promoMap = activePromotionLookupService.findForProducts(products);

        List<CollectionProductResponse> items = rows.getContent().stream()
                .map(cp -> toMembershipResponse(cp, promoMap.get(cp.getProduct().getId())))
                .toList();

        return new PagedResponse<>(new PageImpl<>(items, pageable, rows.getTotalElements()));
    }

    @Override
    @Transactional
    public CollectionProductResponse addCollectionProduct(
            UUID companyId, UUID collectionId, UUID ownerId, AddCollectionProductRequest request) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PRODUCTS);

        Collection collection = collectionRepository.findByIdAndCompanyId(collectionId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Collection not found"));
        if (collection.getType() == CollectionType.DYNAMIC) {
            throw new BadRequestException("Dynamic collections derive their members from rules — edit rules instead.");
        }

        Product product = productRepository.findByIdAndCompanyId(request.getProductId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found in this company"));

        if (collectionProductRepository.existsByCollectionIdAndProductId(collectionId, product.getId())) {
            throw new ConflictException("Product is already in this collection");
        }

        CollectionProduct cp = new CollectionProduct();
        cp.setCollection(collection);
        cp.setProduct(product);
        cp.setBoostWeight(request.getBoostWeight());
        cp.setPinnedRank(request.getPinnedRank());
        cp.setSource(CollectionMembershipSource.MANUAL);
        CollectionProduct saved = collectionProductRepository.save(cp);

        publishReindex(companyId, List.of(product.getId()));
        evictAfterCommit(() -> evictCollectionCaches(companyId, collectionId, collection.getSlug()));

        return toMembershipResponse(saved, null);
    }

    @Override
    @Transactional
    public CollectionProductResponse updateCollectionProduct(
            UUID companyId, UUID collectionId, UUID productId, UUID ownerId,
            UpdateCollectionProductRequest request) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PRODUCTS);

        Collection collection = collectionRepository.findByIdAndCompanyId(collectionId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Collection not found"));
        CollectionProduct cp = collectionProductRepository
                .findByCollectionIdAndProductId(collectionId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product is not in this collection"));

        cp.setBoostWeight(request.getBoostWeight());
        cp.setPinnedRank(request.getPinnedRank());
        CollectionProduct saved = collectionProductRepository.save(cp);

        publishReindex(companyId, List.of(productId));
        evictAfterCommit(() -> evictCollectionCaches(companyId, collectionId, collection.getSlug()));

        return toMembershipResponse(saved, null);
    }

    @Override
    @Transactional
    public void removeCollectionProduct(UUID companyId, UUID collectionId, UUID productId, UUID ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PRODUCTS);

        Collection collection = collectionRepository.findByIdAndCompanyId(collectionId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Collection not found"));
        CollectionProduct cp = collectionProductRepository
                .findByCollectionIdAndProductId(collectionId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product is not in this collection"));

        collectionProductRepository.delete(cp);
        publishReindex(companyId, List.of(productId));
        evictAfterCommit(() -> evictCollectionCaches(companyId, collectionId, collection.getSlug()));
    }

    @Override
    @Transactional
    public CollectionResponse refreshCollection(UUID companyId, UUID collectionId, UUID ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PRODUCTS);

        Collection collection = collectionRepository.findByIdAndCompanyId(collectionId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Collection not found"));

        if (collection.getType() != CollectionType.DYNAMIC) {
            throw new BadRequestException("Only dynamic collections can be refreshed");
        }
        materialiseDynamicMembers(collection);
        Collection saved = collectionRepository.save(collection);
        evictAfterCommit(() -> evictCollectionCaches(companyId, collectionId, saved.getSlug()));
        return toResponse(saved);
    }

    // -------------------------------------------------------------------------
    // Marketplace reads
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<CollectionResponse> listFeaturedForMarketplace(UUID marketplaceId) {
        if (!marketplaceProfileRepository.existsByCompanyId(marketplaceId)) {
            throw new ResourceNotFoundException("Marketplace not found");
        }
        String cacheKey = "marketplace:collections:featured:" + marketplaceId;
        return singleFlightCache.getOrLoad(cacheKey, 300, () ->
                collectionRepository.findFeaturedForMarketplace(marketplaceId)
                        .stream()
                        .map(this::toResponse)
                        .toList(),
                new com.fasterxml.jackson.core.type.TypeReference<List<CollectionResponse>>() {});
    }

    @Override
    @Transactional(readOnly = true)
    public List<CollectionResponse> listFeaturedForVendor(UUID marketplaceId, UUID vendorId) {
        if (!marketplaceProfileRepository.existsByCompanyId(marketplaceId)) {
            throw new ResourceNotFoundException("Marketplace not found");
        }
        String cacheKey = "marketplace:vendor:collections:featured:" + marketplaceId + ":" + vendorId;
        return singleFlightCache.getOrLoad(cacheKey, 300, () ->
                collectionRepository.findFeaturedForVendor(vendorId)
                        .stream()
                        .map(this::toResponse)
                        .toList(),
                new com.fasterxml.jackson.core.type.TypeReference<List<CollectionResponse>>() {});
    }

    @Override
    @Transactional(readOnly = true)
    public CollectionResponse getCollectionBySlug(UUID marketplaceId, String slug) {
        if (!marketplaceProfileRepository.existsByCompanyId(marketplaceId)) {
            throw new ResourceNotFoundException("Marketplace not found");
        }
        String cacheKey = "marketplace:collection:" + marketplaceId + ":" + slug;
        return singleFlightCache.getOrLoad(cacheKey, 300, () -> {
            Collection c = findActiveBySlugForMarketplace(marketplaceId, slug);
            return toResponse(c);
        }, CollectionResponse.class);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<CollectionProductResponse> listMarketplaceCollectionProducts(
            UUID marketplaceId, String slug, int page, int size) {
        if (!marketplaceProfileRepository.existsByCompanyId(marketplaceId)) {
            throw new ResourceNotFoundException("Marketplace not found");
        }
        Collection collection = findActiveBySlugForMarketplace(marketplaceId, slug);

        Pageable pageable = PageRequest.of(page, Math.min(size, 50));

        // Only surface ACTIVE, marketplace-listed products belonging to this marketplace —
        // filtering at the query level keeps totalElements/totalPages accurate for shoppers.
        Page<CollectionProduct> rows = collectionProductRepository
                .findVisibleByCollectionIdRanked(collection.getId(), marketplaceId, pageable);

        List<Product> products = rows.getContent().stream().map(CollectionProduct::getProduct).toList();
        Map<UUID, ActivePromotionSummary> promoMap = activePromotionLookupService.findForProducts(products);

        return new PagedResponse<>(rows.map(cp -> toMembershipResponse(cp, promoMap.get(cp.getProduct().getId()))));
    }

    // -------------------------------------------------------------------------
    // Internals — used by both this service and DynamicCollectionWorker
    // -------------------------------------------------------------------------

    /**
     * Replaces all AUTO membership rows for a DYNAMIC collection with the current rule-matched
     * product set. MANUAL rows are preserved so hand-picked extras don't get scrubbed on each
     * refresh. Publishes reindex events for the symmetric diff so ES collectionIds stay in sync.
     */
    @Transactional
    public void materialiseDynamicMembers(Collection collection) {
        if (collection.getType() != CollectionType.DYNAMIC) {
            return;
        }
        DynamicCollectionRules rules = DynamicCollectionRules.parse(collection.getRulesJson(), objectMapper);
        UUID companyId = collection.getCompany().getId();

        // Paginate the active-product scan to avoid loading the entire catalog into heap
        // for companies with large catalogs. Each page is filtered in memory and diffed
        // against the existing AUTO members.
        final int PAGE_SIZE = 500;
        Set<UUID> matched = new HashSet<>();
        int pageIdx = 0;
        org.springframework.data.domain.Page<Product> page;
        do {
            org.springframework.data.domain.Pageable pageable =
                    PageRequest.of(pageIdx++, PAGE_SIZE);
            page = productRepository.findAll(
                    ProductSpecification.withFilters(
                            companyId, null, null, null, null, null, null,
                            ProductStatus.ACTIVE, null, null, null),
                    pageable);
            page.getContent().stream()
                    .filter(p -> matchesRule(p, rules))
                    .map(Product::getId)
                    .forEach(matched::add);
        } while (page.hasNext());

        Set<UUID> existingAuto = collectionProductRepository
                .findAllByCollectionIdAndSource(collection.getId(), CollectionMembershipSource.AUTO)
                .stream()
                .map(cp -> cp.getProduct().getId())
                .collect(Collectors.toSet());

        Set<UUID> toAdd = new HashSet<>(matched);
        toAdd.removeAll(existingAuto);

        Set<UUID> toRemove = new HashSet<>(existingAuto);
        toRemove.removeAll(matched);

        // Diff applied row-by-row; volumes per collection per pass are small. A bulk delete-and-
        // recreate would churn the table and break downstream cache eviction.
        if (!toRemove.isEmpty()) {
            for (UUID productId : toRemove) {
                collectionProductRepository.findByCollectionIdAndProductId(collection.getId(), productId)
                        .filter(cp -> cp.getSource() == CollectionMembershipSource.AUTO)
                        .ifPresent(collectionProductRepository::delete);
            }
        }
        if (!toAdd.isEmpty()) {
            for (UUID productId : toAdd) {
                if (collectionProductRepository.existsByCollectionIdAndProductId(collection.getId(), productId)) {
                    // A MANUAL row already exists — don't shadow it with an AUTO row.
                    continue;
                }
                Product p = productRepository.getReferenceById(productId);
                CollectionProduct cp = new CollectionProduct();
                cp.setCollection(collection);
                cp.setProduct(p);
                cp.setSource(CollectionMembershipSource.AUTO);
                collectionProductRepository.save(cp);
            }
        }

        collection.setLastMaterialisedAt(Instant.now());

        // Reindex the symmetric diff so the collectionIds array on each ProductDocument matches.
        Set<UUID> affected = new HashSet<>(toAdd);
        affected.addAll(toRemove);
        publishReindex(companyId, new ArrayList<>(affected));

        if (!affected.isEmpty()) {
            log.info("[COLLECTION] Dynamic collection {} materialised: +{} / -{} products",
                    collection.getId(), toAdd.size(), toRemove.size());
        }
    }

    private static boolean matchesRule(Product p, DynamicCollectionRules rules) {
        if (!rules.tagsAnyOf().isEmpty() && !anyTagMatches(p.getTags(), rules.tagsAnyOf())) {
            return false;
        }
        if (!rules.categoriesAnyOf().isEmpty()
                && (p.getCategory() == null || !rules.categoriesAnyOf().contains(p.getCategory().toLowerCase(Locale.ROOT)))) {
            return false;
        }
        if (!rules.brandsAnyOf().isEmpty()
                && (p.getBrand() == null || !rules.brandsAnyOf().contains(p.getBrand().toLowerCase(Locale.ROOT)))) {
            return false;
        }
        return true;
    }

    private static boolean anyTagMatches(String tags, List<String> targets) {
        if (tags == null || tags.isBlank()) return false;
        // Tags are stored as a free-form comma/space separated string — split on either.
        String lower = tags.toLowerCase(Locale.ROOT);
        for (String t : targets) {
            // Substring check is the right behaviour here: free-text tag fields don't carry a
            // strict separator contract, so we treat them as a bag of words.
            if (lower.contains(t)) return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Collection findActiveBySlugForMarketplace(UUID marketplaceId, String slug) {
        // The slug is unique per company. A collection only appears on a marketplace when one of
        // the owning company's products is listed there. Resolve by scanning vendor companies.
        List<UUID> vendorCompanyIds = productRepository.findMarketplaceListed(marketplaceId).stream()
                .map(p -> p.getCompany().getId())
                .distinct()
                .toList();
        for (UUID companyId : vendorCompanyIds) {
            Collection c = collectionRepository.findBySlugAndCompanyId(slug, companyId).orElse(null);
            if (c != null && c.getStatus() == CollectionStatus.ACTIVE) {
                return c;
            }
        }
        throw new ResourceNotFoundException("Collection not found");
    }

    private void publishReindex(UUID companyId, java.util.Collection<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) return;
        List<Product> products = productRepository.findAllByIdInAndCompanyId(productIds, companyId);
        for (Product p : products) {
            eventPublisher.publishEvent(new ProductIndexEvent(p, p.getCompany().getId()));
        }
    }

    private void evictAfterCommit(Runnable eviction) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { eviction.run(); }
            });
        } else {
            eviction.run();
        }
    }

    private void evictCollectionCaches(UUID companyId, UUID collectionId, String slug) {
        try {
            singleFlightCache.evict("collection:" + companyId + ":" + collectionId);
            singleFlightCache.evictByPattern("collections:list:" + companyId + ":*");
            singleFlightCache.evictByPattern("marketplace:collection:*:" + slug);
            singleFlightCache.evictByPattern("marketplace:collections:featured:*");
            singleFlightCache.evictByPattern("marketplace:vendor:collections:featured:*");
        } catch (Exception e) {
            // Redis convention: never let a cache failure propagate to the write path.
            log.warn("[COLLECTION] Cache eviction failed for collectionId={}: {}", collectionId, e.getMessage());
        }
    }

    private void assertCompanyExists(UUID companyId) {
        if (!companyRepository.existsById(companyId)) {
            throw new ResourceNotFoundException("Company not found with id: " + companyId);
        }
    }

    private static String normaliseSlug(String slug) {
        return slug == null ? null : slug.trim().toLowerCase(Locale.ROOT);
    }

    private CollectionResponse toResponse(Collection c) {
        long count;
        try {
            count = collectionProductRepository.countByCollectionId(c.getId());
        } catch (Exception e) {
            count = 0;
        }
        return new CollectionResponse(
                c.getId(),
                c.getCompany() != null ? c.getCompany().getId() : null,
                c.getName(),
                c.getSlug(),
                c.getDescription(),
                c.getImageUrl(),
                c.getType().name(),
                c.getStatus().name(),
                c.isFeatured(),
                c.getFeaturedRank(),
                c.getRulesJson(),
                count,
                c.getLastMaterialisedAt(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }

    private CollectionProductResponse toMembershipResponse(CollectionProduct cp, ActivePromotionSummary promo) {
        Product p = cp.getProduct();
        return new CollectionProductResponse(
                cp.getId(),
                cp.getCollection().getId(),
                p.getId(),
                p.getName(),
                p.getSku(),
                p.getThumbnailUrl(),
                p.getPrice(),
                p.getCurrency(),
                p.getStatus().name(),
                cp.getBoostWeight(),
                cp.getPinnedRank(),
                cp.getSource().name(),
                cp.getCreatedAt(),
                promo
        );
    }
}
