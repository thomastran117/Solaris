package backend.services.impl.products;

import java.util.UUID;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.FieldValueFactorScoreFunction;
import co.elastic.clients.elasticsearch._types.query_dsl.FieldValueFactorModifier;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionBoostMode;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionScore;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionScoreMode;
import co.elastic.clients.elasticsearch._types.query_dsl.FunctionScoreQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.documents.ProductDocument;
import backend.events.ProductIndexEvent;
import backend.events.ProductRemoveEvent;
import backend.events.products.PriceDropAlertEvent;
import backend.dtos.requests.product.AddProductImageRequest;
import backend.dtos.requests.product.BatchCreateProductsRequest;
import backend.dtos.requests.product.BatchDeleteProductsRequest;
import backend.dtos.requests.product.BatchUpdateProductsRequest;
import backend.dtos.requests.product.CreateProductOptionRequest;
import backend.dtos.requests.product.CreateProductRequest;
import backend.dtos.requests.product.CreateProductVariantRequest;
import backend.dtos.requests.product.ReorderProductImagesRequest;
import backend.dtos.requests.product.RevertProductChangesRequest;
import backend.dtos.requests.product.SetProductAttributesRequest;
import backend.dtos.requests.product.UpdateProductMerchandisingRequest;
import backend.dtos.requests.product.UpdateProductOptionRequest;
import backend.dtos.requests.product.UpdateProductRequest;
import backend.dtos.requests.product.UpdateProductVariantRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.product.ActivePromotionSummary;
import backend.dtos.responses.product.CatalogSearchResponse;
import backend.dtos.responses.product.ProductAttributeResponse;
import backend.dtos.responses.product.ProductHistoryEntryResponse;
import backend.dtos.responses.search.FacetBucket;
import backend.dtos.responses.search.PriceRangeBucket;
import backend.dtos.responses.search.SearchFacets;
import backend.dtos.responses.product.ProductImageResponse;
import backend.dtos.responses.product.ProductOptionResponse;
import backend.dtos.responses.product.ProductResponse;
import backend.dtos.responses.product.ProductVariantResponse;
import org.springframework.dao.DataIntegrityViolationException;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.exceptions.http.ServiceUnavaliableException;
import backend.models.core.Company;
import backend.models.core.InventoryAdjustment;
import backend.models.core.Product;
import backend.models.core.ProductAttribute;
import backend.models.core.ProductChangeLog;
import backend.models.core.ProductImage;
import backend.models.core.ProductOption;
import backend.models.core.ProductVariant;
import backend.models.enums.ProductStatus;
import backend.dtos.requests.product.UpdateMarketplaceListingRequest;
import backend.dtos.responses.product.MarketplaceCatalogProductResponse;
import backend.dtos.responses.product.VendorStorefrontResponse;
import backend.models.core.MarketplaceVendor;
import backend.dtos.requests.product.AddProductRelationshipRequest;
import backend.dtos.responses.product.ProductRelationshipResponse;
import backend.dtos.responses.product.SimilarProductResponse;
import backend.models.core.ProductRelationship;
import backend.models.enums.ProductRelationshipType;
import backend.repositories.BundleRepository;
import backend.repositories.ProductRelationshipRepository;
import backend.repositories.ProductSimilarityRepository;
import backend.models.core.ProductSimilarity;
import backend.repositories.CollectionProductRepository;
import backend.repositories.CompanyRepository;
import backend.repositories.InventoryAdjustmentRepository;
import backend.repositories.MarketplaceProfileRepository;
import backend.repositories.MarketplaceVendorRepository;
import backend.repositories.ProductAttributeRepository;
import backend.repositories.ProductChangeLogRepository;
import backend.repositories.ProductImageRepository;
import backend.repositories.ProductOptionRepository;
import backend.repositories.ProductRepository;
import backend.repositories.ProductReviewRepository;
import backend.repositories.ProductVariantRepository;
import backend.repositories.PromotionRuleRepository;
import backend.repositories.specifications.ProductSpecification;
import backend.services.impl.pricing.ActivePromotionLookupService;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.products.ProductChangeLogger;
import backend.services.intf.products.ProductService;
import backend.models.enums.CompanyCapability;
import backend.models.enums.ChangeSource;
import org.springframework.context.ApplicationEventPublisher;

import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import backend.services.impl.SingleFlightCache;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ProductServiceImpl implements ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductServiceImpl.class);
    private static final Set<String> SORTABLE_FIELDS = Set.of("name", "price", "createdAt", "stock");

    private final ProductRepository productRepository;
    private final CompanyRepository companyRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductOptionRepository productOptionRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductAttributeRepository productAttributeRepository;
    private final ProductReviewRepository productReviewRepository;
    private final BundleRepository bundleRepository;
    private final CollectionProductRepository collectionProductRepository;
    private final PromotionRuleRepository promotionRuleRepository;
    private final MarketplaceProfileRepository marketplaceProfileRepository;
    private final MarketplaceVendorRepository marketplaceVendorRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ElasticsearchOperations elasticsearchOperations;
    private final SingleFlightCache singleFlightCache;
    private final ActivePromotionLookupService activePromotionLookupService;
    private final ProductChangeLogger productChangeLogger;
    private final ProductChangeLogRepository productChangeLogRepository;
    private final InventoryAdjustmentRepository inventoryAdjustmentRepository;
    private final ProductRelationshipRepository productRelationshipRepository;
    private final ProductSimilarityRepository productSimilarityRepository;
    private final CompanyAccessService companyAccessService;
    private final long cacheTtl;
    private final long cacheTtlShort;

    public ProductServiceImpl(
            ProductRepository productRepository,
            CompanyRepository companyRepository,
            ProductImageRepository productImageRepository,
            ProductOptionRepository productOptionRepository,
            ProductVariantRepository productVariantRepository,
            ProductAttributeRepository productAttributeRepository,
            ProductReviewRepository productReviewRepository,
            BundleRepository bundleRepository,
            CollectionProductRepository collectionProductRepository,
            PromotionRuleRepository promotionRuleRepository,
            MarketplaceProfileRepository marketplaceProfileRepository,
            MarketplaceVendorRepository marketplaceVendorRepository,
            ApplicationEventPublisher eventPublisher,
            ElasticsearchOperations elasticsearchOperations,
            SingleFlightCache singleFlightCache,
            ActivePromotionLookupService activePromotionLookupService,
            ProductChangeLogger productChangeLogger,
            ProductChangeLogRepository productChangeLogRepository,
            InventoryAdjustmentRepository inventoryAdjustmentRepository,
            ProductRelationshipRepository productRelationshipRepository,
            ProductSimilarityRepository productSimilarityRepository,
            CompanyAccessService companyAccessService,
            @Value("${app.product.cache-ttl-seconds:300}") long cacheTtl,
            @Value("${app.product.cache-ttl-short-seconds:60}") long cacheTtlShort) {
        this.productRepository = productRepository;
        this.companyRepository = companyRepository;
        this.productImageRepository = productImageRepository;
        this.productOptionRepository = productOptionRepository;
        this.productVariantRepository = productVariantRepository;
        this.productAttributeRepository = productAttributeRepository;
        this.productReviewRepository = productReviewRepository;
        this.bundleRepository = bundleRepository;
        this.collectionProductRepository = collectionProductRepository;
        this.promotionRuleRepository = promotionRuleRepository;
        this.marketplaceProfileRepository = marketplaceProfileRepository;
        this.marketplaceVendorRepository = marketplaceVendorRepository;
        this.eventPublisher = eventPublisher;
        this.elasticsearchOperations = elasticsearchOperations;
        this.singleFlightCache = singleFlightCache;
        this.activePromotionLookupService = activePromotionLookupService;
        this.productChangeLogger = productChangeLogger;
        this.productChangeLogRepository = productChangeLogRepository;
        this.inventoryAdjustmentRepository = inventoryAdjustmentRepository;
        this.productRelationshipRepository = productRelationshipRepository;
        this.productSimilarityRepository = productSimilarityRepository;
        this.companyAccessService = companyAccessService;
        this.cacheTtl = cacheTtl;
        this.cacheTtlShort = cacheTtlShort;
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<ProductResponse> searchProducts(
            UUID companyId,
            String q,
            String category,
            String brand,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean featured,
            ProductStatus status,
            Boolean listed,
            String discountCategory,
            Boolean hasDiscount,
            int page,
            int size,
            String sort,
            String direction) {

        assertCompanyExists(companyId);
        if (page > 10_000) {
            throw new BadRequestException("Page number too large. Maximum page is 10,000 (roughly 500,000 products at max page size).");
        }
        final int clampedSize = Math.min(size, 50);
        // Normalize nulls to "" so cache keys are canonical regardless of whether params
        // are omitted vs. explicitly null (prevents duplicate entries for the same query).
        String cacheKey = String.format("products:search:%s:%s:%s:%s:%s:%s:%s:%s:%s:%s:%s:%d:%d:%s:%s",
                companyId,
                q            != null ? q            : "",
                category     != null ? category     : "",
                brand        != null ? brand        : "",
                minPrice     != null ? minPrice     : "",
                maxPrice     != null ? maxPrice     : "",
                featured     != null ? featured     : "",
                status       != null ? status       : "",
                listed       != null ? listed       : "",
                discountCategory != null ? discountCategory : "",
                hasDiscount  != null ? hasDiscount  : "",
                page, clampedSize,
                sort      != null ? sort      : "",
                direction != null ? direction : "");
        return singleFlightCache.getOrLoad(cacheKey, cacheTtl, () -> {
            String sortField = (sort != null && SORTABLE_FIELDS.contains(sort)) ? sort : "createdAt";
            Sort.Direction sortDir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
            Pageable pageable = PageRequest.of(page, clampedSize, Sort.by(sortDir, sortField));

            // --- Elasticsearch path ---
            try {
                final String companyIdStr = companyId.toString();
                BoolQuery.Builder bq = new BoolQuery.Builder()
                        .filter(TermQuery.of(t -> t.field("companyId").value(companyIdStr))._toQuery());

                if (q != null && !q.isBlank()) {
                    bq.must(MultiMatchQuery.of(mm -> mm
                            .fields("name^3", "description", "brand^2", "category", "tags")
                            .query(q)
                            .fuzziness("AUTO"))._toQuery());
                }
                if (status           != null) bq.filter(TermQuery.of(t -> t.field("status").value(status.name()))._toQuery());
                if (category         != null) bq.filter(TermQuery.of(t -> t.field("category").value(category))._toQuery());
                if (brand            != null) bq.filter(TermQuery.of(t -> t.field("brand").value(brand))._toQuery());
                if (featured         != null) bq.filter(TermQuery.of(t -> t.field("featured").value(featured))._toQuery());
                if (listed           != null) bq.filter(TermQuery.of(t -> t.field("listed").value(listed))._toQuery());
                if (discountCategory != null) bq.filter(TermQuery.of(t -> t.field("discountCategories").value(discountCategory.trim().toLowerCase()))._toQuery());
                if (hasDiscount      != null) bq.filter(TermQuery.of(t -> t.field("hasActiveDiscount").value(hasDiscount))._toQuery());
                if (minPrice != null || maxPrice != null) {
                    final Double minVal = minPrice != null ? minPrice.doubleValue() : null;
                    final Double maxVal = maxPrice != null ? maxPrice.doubleValue() : null;
                    bq.filter(co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery.of(r -> r.number(n -> {
                        n.field("price");
                        if (minVal != null) n.gte(minVal);
                        if (maxVal != null) n.lte(maxVal);
                        return n;
                    }))._toQuery());
                }

                NativeQuery esQuery = NativeQuery.builder()
                        .withQuery(applyMerchandisingScore(bq.build()._toQuery()))
                        .withPageable(pageable)
                        .build();

                SearchHits<ProductDocument> hits = elasticsearchOperations.search(esQuery, ProductDocument.class);
                List<UUID> ids = hits.stream().map(h -> h.getContent().getId()).toList();

                Map<UUID, Product> productMap = productRepository
                        .findAllByIdInAndCompanyId(ids, companyId)
                        .stream()
                        .collect(Collectors.toMap(Product::getId, p -> p));

                Map<UUID, ActivePromotionSummary> promoMap =
                        activePromotionLookupService.findForProducts(productMap.values());

                List<ProductResponse> content = ids.stream()
                        .filter(productMap::containsKey)
                        .map(id -> toResponse(productMap.get(id), promoMap.get(id)))
                        .toList();

                return new PagedResponse<>(new PageImpl<>(content, pageable, hits.getTotalHits()));

            } catch (Exception e) {
                log.warn("[SEARCH] Elasticsearch unavailable, falling back to database: {}", e.getMessage());
            }

            // --- JPA fallback ---
            Page<Product> jpaPage = productRepository.findAll(
                    ProductSpecification.withFilters(companyId, q, category, brand, minPrice, maxPrice, featured, status, listed, discountCategory, hasDiscount),
                    pageable);
            Map<UUID, ActivePromotionSummary> jpaPromoMap =
                    activePromotionLookupService.findForProducts(jpaPage.getContent());
            return new PagedResponse<>(jpaPage.map(p -> toResponse(p, jpaPromoMap.get(p.getId()))));
        }, new TypeReference<PagedResponse<ProductResponse>>() {});
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProduct(UUID companyId, UUID productId) {
        assertCompanyExists(companyId);
        String cacheKey = "product:" + companyId + ":" + productId;
        return singleFlightCache.getOrLoad(cacheKey, cacheTtl, () -> {
            Product product = productRepository.findByIdAndCompanyId(productId, companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));
            ActivePromotionSummary promo = activePromotionLookupService
                    .findForProducts(List.of(product))
                    .get(productId);
            List<ProductRelationshipResponse> relationships = productRelationshipRepository
                    .findAllBySourceProductIdAndSourceProductCompanyId(productId, companyId)
                    .stream()
                    .map(this::toRelationshipResponse)
                    .toList();
            List<ProductImageResponse> images = product.getImages().stream().map(this::toImageResponse).toList();
            List<ProductOptionResponse> options = product.getOptions().stream().map(this::toOptionResponse).toList();
            List<ProductVariantResponse> variants = product.getVariants().stream().map(this::toVariantResponse).toList();
            List<ProductAttributeResponse> attributes = product.getAttributes().stream().map(this::toAttrResponse).toList();
            return toResponseWithRating(product, images, options, variants, attributes, relationships, null, 0L, promo);
        }, ProductResponse.class);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getProductsByIds(UUID companyId, List<UUID> ids) {
        if (ids.size() > 1000) {
            throw new BadRequestException("Cannot fetch more than 1000 products at once");
        }
        assertCompanyExists(companyId);
        String sortedIds = ids.stream().sorted().map(String::valueOf).collect(Collectors.joining(":"));
        String cacheKey = "products:batch:" + companyId + ":" + sortedIds;
        return singleFlightCache.getOrLoad(cacheKey, cacheTtlShort, () -> {
            List<Product> products = productRepository.findAllByIdInAndCompanyId(ids, companyId);
            Map<UUID, ActivePromotionSummary> promoMap = activePromotionLookupService.findForProducts(products);
            return products.stream()
                    .map(p -> toResponse(p, promoMap.get(p.getId())))
                    .toList();
        }, new TypeReference<List<ProductResponse>>() {});
    }

    @Override
    @Transactional
    public ProductResponse createProduct(UUID companyId, UUID ownerId, CreateProductRequest request) {
        Company company = companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PRODUCTS);

        if (request.getSku() != null && !request.getSku().isBlank()
                && productRepository.existsBySkuAndCompanyId(request.getSku(), companyId)) {
            throw new ConflictException("A product with this SKU already exists in this company");
        }

        Product product = new Product();
        product.setCompany(company);
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setSku(request.getSku());
        product.setPrice(request.getPrice());
        product.setCompareAtPrice(request.getCompareAtPrice());
        product.setCurrency(request.getCurrency() != null ? request.getCurrency() : "USD");
        product.setCategory(request.getCategory());
        product.setBrand(request.getBrand());
        product.setTags(request.getTags());
        product.setThumbnailUrl(request.getThumbnailUrl());
        product.setStock(request.getStock());
        product.setWeight(request.getWeight());
        product.setWeightUnit(request.getWeightUnit());
        product.setFeatured(request.isFeatured());
        product.setPurchasable(request.isPurchasable());
        product.setListed(request.isListed());

        ProductStatus initialStatus = request.getStatus() != null ? request.getStatus() : ProductStatus.DRAFT;
        applyStatusTransition(product, initialStatus, request.getScheduledPublishAt(), true);

        Product saved;
        try {
            saved = productRepository.save(product);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("A product with this SKU already exists in this company");
        }
        productChangeLogger.logCreate(saved, ChangeSource.USER);
        eventPublisher.publishEvent(new ProductIndexEvent(saved, saved.getCompany().getId()));
        evictAfterCommit(() -> {
            singleFlightCache.evictByPattern("products:search:" + companyId + ":*");
            singleFlightCache.evictByPattern("products:batch:" + companyId + ":*");
        });
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(UUID companyId, UUID productId, UUID ownerId, UpdateProductRequest request) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PRODUCTS);

        Product product = productRepository.findByIdAndCompanyId(productId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        Product before = productChangeLogger.snapshot(product);
        ProductStatus originalStatus = product.getStatus();
        boolean originalListed = product.isListed();

        if (request.getSku() != null && !request.getSku().equals(product.getSku())) {
            if (productRepository.existsBySkuAndCompanyId(request.getSku(), companyId)) {
                throw new ConflictException("A product with this SKU already exists in this company");
            }
            product.setSku(request.getSku());
        }

        if (request.getName() != null) product.setName(request.getName());
        if (request.getDescription() != null) product.setDescription(request.getDescription());
        if (request.getPrice() != null) product.setPrice(request.getPrice());
        if (request.getCompareAtPrice() != null) product.setCompareAtPrice(request.getCompareAtPrice());
        if (request.getCurrency() != null) product.setCurrency(request.getCurrency());
        if (request.getCategory() != null) product.setCategory(request.getCategory());
        if (request.getBrand() != null) product.setBrand(request.getBrand());
        if (request.getTags() != null) product.setTags(request.getTags());
        if (request.getThumbnailUrl() != null) product.setThumbnailUrl(request.getThumbnailUrl());
        if (request.getWeight() != null) product.setWeight(request.getWeight());
        if (request.getWeightUnit() != null) product.setWeightUnit(request.getWeightUnit());
        if (request.getStatus() != null || request.getScheduledPublishAt() != null) {
            ProductStatus targetStatus = request.getStatus() != null ? request.getStatus() : product.getStatus();
            applyStatusTransition(product, targetStatus, request.getScheduledPublishAt(), false);
        }
        if (request.getFeatured() != null) product.setFeatured(request.getFeatured());
        if (request.getPurchasable() != null) product.setPurchasable(request.getPurchasable());
        if (request.getListed() != null) product.setListed(request.getListed());
        if (request.getPreorderEnabled() != null) product.setPreorderEnabled(request.getPreorderEnabled());
        if (request.getPreorderExpectedDate() != null) product.setPreorderExpectedDate(request.getPreorderExpectedDate());

        // Merchandising — pinning a product with an expired window is rejected outright so the
        // admin sees a clear validation error rather than a silent reset on next reindex.
        if (request.getPinnedUntil() != null && !request.getPinnedUntil().isAfter(Instant.now())) {
            throw new BadRequestException("pinnedUntil must be in the future");
        }
        if (request.getBoostWeight() != null) product.setBoostWeight(request.getBoostWeight());
        if (request.getPinnedUntil() != null) {
            product.setPinnedUntil(request.getPinnedUntil());
            product.setPinnedRank(request.getPinnedRank());
        } else if (request.getPinnedRank() != null) {
            // Rank-only update with the window already set: just update the rank.
            product.setPinnedRank(request.getPinnedRank());
        }

        boolean activating = request.getStatus() == ProductStatus.ACTIVE && originalStatus != ProductStatus.ACTIVE;
        boolean listing    = Boolean.TRUE.equals(request.getListed()) && !originalListed;

        if ((activating || listing) && productImageRepository.countByProductId(productId) < 1) {
            throw new BadRequestException("Product must have at least one image before it can be made active or listed");
        }

        Product saved = productRepository.save(product);
        productChangeLogger.logUpdate(before, saved, ChangeSource.USER, null);
        if (before.getPrice() != null && saved.getPrice() != null
                && saved.getPrice().compareTo(before.getPrice()) < 0) {
            eventPublisher.publishEvent(new PriceDropAlertEvent(productId, before.getPrice(), saved.getPrice()));
        }
        eventPublisher.publishEvent(new ProductIndexEvent(saved, saved.getCompany().getId()));
        final UUID marketplaceId = saved.getMarketplaceId();
        evictAfterCommit(() -> {
            singleFlightCache.evict("product:" + companyId + ":" + productId);
            singleFlightCache.evictByPattern("products:search:" + companyId + ":*");
            singleFlightCache.evictByPattern("products:batch:" + companyId + ":*");
            if (marketplaceId != null) {
                singleFlightCache.evict("marketplace:product:" + marketplaceId + ":" + productId);
                singleFlightCache.evictByPattern("marketplace:search:" + marketplaceId + ":*");
                singleFlightCache.evictByPattern("marketplace:storefront:" + marketplaceId + ":*");
            }
        });
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteProduct(UUID companyId, UUID productId, UUID ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PRODUCTS);

        Product product = productRepository.findByIdAndCompanyId(productId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        if (bundleRepository.existsByItemsProductId(productId)) {
            throw new ConflictException("Product is part of one or more bundles. Remove it from all bundles before deleting.");
        }

        final UUID marketplaceId = product.getMarketplaceId();
        productChangeLogger.logDelete(product);
        promotionRuleRepository.removeProductFromAllRules(productId);
        collectionProductRepository.deleteAllByProductId(productId);
        productRepository.delete(product);
        eventPublisher.publishEvent(new ProductRemoveEvent(product.getId(), marketplaceId));
        evictAfterCommit(() -> {
            singleFlightCache.evict("product:" + companyId + ":" + productId);
            singleFlightCache.evictByPattern("products:search:" + companyId + ":*");
            singleFlightCache.evictByPattern("products:batch:" + companyId + ":*");
            if (marketplaceId != null) {
                singleFlightCache.evict("marketplace:product:" + marketplaceId + ":" + productId);
                singleFlightCache.evictByPattern("marketplace:search:" + marketplaceId + ":*");
                singleFlightCache.evictByPattern("marketplace:storefront:" + marketplaceId + ":*");
            }
        });
    }

    @Override
    @Transactional
    public List<ProductResponse> batchCreateProducts(UUID companyId, UUID ownerId, BatchCreateProductsRequest request) {
        Company company = companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PRODUCTS);

        List<ProductResponse> results = new java.util.ArrayList<>();
        Set<String> batchSkus = new HashSet<>();

        for (CreateProductRequest req : request.getProducts()) {
            if (req.getSku() != null && !req.getSku().isBlank()) {
                if (!batchSkus.add(req.getSku().toLowerCase())) {
                    throw new ConflictException("Duplicate SKU '" + req.getSku() + "' within this batch");
                }
                if (productRepository.existsBySkuAndCompanyId(req.getSku(), companyId)) {
                    throw new ConflictException("A product with SKU '" + req.getSku() + "' already exists in this company");
                }
            }

            Product product = new Product();
            product.setCompany(company);
            product.setName(req.getName());
            product.setDescription(req.getDescription());
            product.setSku(req.getSku());
            product.setPrice(req.getPrice());
            product.setCompareAtPrice(req.getCompareAtPrice());
            product.setCurrency(req.getCurrency() != null ? req.getCurrency().toUpperCase() : "USD");
            product.setCategory(req.getCategory());
            product.setBrand(req.getBrand());
            product.setTags(req.getTags());
            product.setThumbnailUrl(req.getThumbnailUrl());
            product.setStock(req.getStock());
            product.setWeight(req.getWeight());
            product.setWeightUnit(req.getWeightUnit());
            product.setFeatured(req.isFeatured());
            product.setPurchasable(req.isPurchasable());
            product.setListed(req.isListed());
            product.setStatus(ProductStatus.DRAFT);

            Product saved = productRepository.save(product);
            productChangeLogger.logCreate(saved, ChangeSource.USER);
            eventPublisher.publishEvent(new ProductIndexEvent(saved, saved.getCompany().getId()));
            results.add(toResponse(saved));
        }

        evictAfterCommit(() -> {
            singleFlightCache.evictByPattern("products:search:" + companyId + ":*");
            singleFlightCache.evictByPattern("products:batch:" + companyId + ":*");
        });
        return results;
    }

    @Override
    @Transactional
    public void batchDeleteProducts(UUID companyId, UUID ownerId, BatchDeleteProductsRequest request) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PRODUCTS);

        List<Product> products = productRepository.findAllByIdInAndCompanyId(request.getIds(), companyId);

        if (products.size() != request.getIds().size()) {
            throw new ResourceNotFoundException("One or more products were not found in this company");
        }

        promotionRuleRepository.removeProductsFromAllRules(request.getIds());
        for (UUID pid : request.getIds()) {
            collectionProductRepository.deleteAllByProductId(pid);
        }
        for (Product p : products) {
            productChangeLogger.logDelete(p);
        }
        productRepository.deleteAll(products);
        for (Product p : products) {
            eventPublisher.publishEvent(new ProductRemoveEvent(p.getId(), p.getMarketplaceId()));
        }
        final List<UUID> deletedIds = products.stream().map(Product::getId).toList();
        final List<UUID> affectedMarketplaces = products.stream()
                .map(Product::getMarketplaceId).filter(Objects::nonNull).distinct().toList();
        evictAfterCommit(() -> {
            for (UUID id : deletedIds) singleFlightCache.evict("product:" + companyId + ":" + id);
            singleFlightCache.evictByPattern("products:search:" + companyId + ":*");
            singleFlightCache.evictByPattern("products:batch:" + companyId + ":*");
            for (UUID mpId : affectedMarketplaces) {
                singleFlightCache.evictByPattern("marketplace:search:" + mpId + ":*");
                singleFlightCache.evictByPattern("marketplace:storefront:" + mpId + ":*");
            }
        });
    }

    @Override
    @Transactional
    public List<ProductResponse> batchUpdateProducts(UUID companyId, UUID ownerId, BatchUpdateProductsRequest request) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PRODUCTS);

        if (request.getStatus() == ProductStatus.SCHEDULED) {
            throw new BadRequestException("SCHEDULED status cannot be set in bulk — use individual product edit to set a publish date");
        }

        List<Product> products = productRepository.findAllByIdInAndCompanyId(request.getIds(), companyId);
        if (products.size() != request.getIds().size()) {
            throw new ResourceNotFoundException("One or more products were not found in this company");
        }

        boolean needsImageCheck = request.getStatus() == ProductStatus.ACTIVE
                || Boolean.TRUE.equals(request.getListed());
        if (needsImageCheck) {
            List<String> missing = new java.util.ArrayList<>();
            for (Product p : products) {
                if (productImageRepository.countByProductId(p.getId()) < 1) {
                    missing.add(p.getName());
                }
            }
            if (!missing.isEmpty()) {
                throw new BadRequestException(
                        "The following products need at least one image before being activated or listed: "
                                + String.join(", ", missing));
            }
        }

        List<ProductResponse> results = new java.util.ArrayList<>();
        Set<UUID> affectedMarketplaces = new java.util.HashSet<>();

        for (Product product : products) {
            Product before = productChangeLogger.snapshot(product);
            if (request.getStatus() != null) {
                applyStatusTransition(product, request.getStatus(), null, false);
            }
            if (request.getFeatured() != null) product.setFeatured(request.getFeatured());
            if (request.getListed() != null) product.setListed(request.getListed());
            if (request.getCategory() != null) product.setCategory(request.getCategory());
            if (request.getBrand() != null) product.setBrand(request.getBrand());

            Product saved = productRepository.save(product);
            productChangeLogger.logUpdate(before, saved, ChangeSource.USER, null);
            eventPublisher.publishEvent(new ProductIndexEvent(saved, companyId));
            if (saved.getMarketplaceId() != null) affectedMarketplaces.add(saved.getMarketplaceId());
            results.add(toResponse(saved));
        }

        final Set<UUID> mpIds = affectedMarketplaces;
        final List<UUID> updatedIds = products.stream().map(Product::getId).toList();
        evictAfterCommit(() -> {
            for (UUID id : updatedIds) singleFlightCache.evict("product:" + companyId + ":" + id);
            singleFlightCache.evictByPattern("products:search:" + companyId + ":*");
            singleFlightCache.evictByPattern("products:batch:" + companyId + ":*");
            for (UUID mpId : mpIds) {
                singleFlightCache.evictByPattern("marketplace:search:" + mpId + ":*");
                singleFlightCache.evictByPattern("marketplace:storefront:" + mpId + ":*");
            }
        });
        return results;
    }

    @Override
    @Transactional
    public ProductResponse duplicateProduct(UUID companyId, UUID productId, UUID ownerId) {
        Company company = companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PRODUCTS);

        Product source = productRepository.findByIdAndCompanyId(productId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        Product copy = new Product();
        copy.setCompany(company);
        copy.setName(source.getName() + " (Copy)");
        copy.setDescription(source.getDescription());
        copy.setSku(null);
        copy.setPrice(source.getPrice());
        copy.setCompareAtPrice(source.getCompareAtPrice());
        copy.setCurrency(source.getCurrency());
        copy.setCategory(source.getCategory());
        copy.setBrand(source.getBrand());
        copy.setTags(source.getTags());
        copy.setThumbnailUrl(source.getThumbnailUrl());
        copy.setStock(source.getStock());
        copy.setWeight(source.getWeight());
        copy.setWeightUnit(source.getWeightUnit());
        copy.setFeatured(false);
        copy.setPurchasable(source.isPurchasable());
        copy.setListed(false);
        copy.setStatus(ProductStatus.DRAFT);
        copy.setLowStockThreshold(source.getLowStockThreshold());
        copy.setLowStockThresholdPercent(source.getLowStockThresholdPercent());
        copy.setMaxStock(source.getMaxStock());
        copy.setAutoRestockEnabled(source.isAutoRestockEnabled());
        copy.setAutoRestockQty(source.getAutoRestockQty());
        copy.setBackorderEnabled(source.isBackorderEnabled());
        copy.setPreorderEnabled(source.isPreorderEnabled());
        copy.setSubscribable(source.isSubscribable());
        copy.setSubscriptionIntervals(source.getSubscriptionIntervals());
        copy.setSubscriptionDiscountPercent(source.getSubscriptionDiscountPercent());

        Product saved = productRepository.save(copy);

        // Copy images
        List<backend.models.core.ProductImage> images = productImageRepository
                .findAllByProductIdOrderByDisplayOrderAsc(source.getId());
        for (int i = 0; i < images.size(); i++) {
            backend.models.core.ProductImage img = new backend.models.core.ProductImage();
            img.setProduct(saved);
            img.setImageUrl(images.get(i).getImageUrl());
            img.setDisplayOrder(i);
            productImageRepository.save(img);
        }

        // Copy options
        List<backend.models.core.ProductOption> options = productOptionRepository
                .findAllByProductIdOrderByPositionAsc(source.getId());
        for (backend.models.core.ProductOption opt : options) {
            backend.models.core.ProductOption newOpt = new backend.models.core.ProductOption();
            newOpt.setProduct(saved);
            newOpt.setName(opt.getName());
            newOpt.setPosition(opt.getPosition());
            productOptionRepository.save(newOpt);
        }

        // Copy variants (sku cleared)
        List<backend.models.core.ProductVariant> variants = productVariantRepository
                .findAllByProductIdOrderByDisplayOrderAsc(source.getId());
        for (backend.models.core.ProductVariant v : variants) {
            backend.models.core.ProductVariant newV = new backend.models.core.ProductVariant();
            newV.setProduct(saved);
            newV.setSku(null);
            newV.setPrice(v.getPrice());
            newV.setCompareAtPrice(v.getCompareAtPrice());
            newV.setStock(v.getStock());
            newV.setLowStockThreshold(v.getLowStockThreshold());
            newV.setLowStockThresholdPercent(v.getLowStockThresholdPercent());
            newV.setMaxStock(v.getMaxStock());
            newV.setAutoRestockEnabled(v.isAutoRestockEnabled());
            newV.setAutoRestockQty(v.getAutoRestockQty());
            newV.setPurchasable(v.isPurchasable());
            newV.setBackorderEnabled(v.isBackorderEnabled());
            newV.setPreorderEnabled(v.isPreorderEnabled());
            newV.setPreorderExpectedDate(v.getPreorderExpectedDate());
            newV.setOption1(v.getOption1());
            newV.setOption2(v.getOption2());
            newV.setOption3(v.getOption3());
            newV.setDisplayOrder(v.getDisplayOrder());
            productVariantRepository.save(newV);
        }

        // Copy attributes
        List<backend.models.core.ProductAttribute> attrs = productAttributeRepository
                .findAllByProductIdOrderByDisplayOrderAsc(source.getId());
        for (backend.models.core.ProductAttribute a : attrs) {
            backend.models.core.ProductAttribute newA = new backend.models.core.ProductAttribute();
            newA.setProduct(saved);
            newA.setName(a.getName());
            newA.setValue(a.getValue());
            newA.setDisplayOrder(a.getDisplayOrder());
            productAttributeRepository.save(newA);
        }

        productChangeLogger.logCreate(saved, ChangeSource.USER);
        eventPublisher.publishEvent(new ProductIndexEvent(saved, companyId));
        evictAfterCommit(() -> {
            singleFlightCache.evictByPattern("products:search:" + companyId + ":*");
            singleFlightCache.evictByPattern("products:batch:" + companyId + ":*");
        });
        return toResponse(saved);
    }

    // --- Images ---

    @Override
    @Transactional(readOnly = true)
    public List<ProductImageResponse> getProductImages(UUID companyId, UUID productId) {
        productRepository.findByIdAndCompanyId(productId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        return productImageRepository.findAllByProductIdOrderByDisplayOrderAsc(productId)
                .stream()
                .map(this::toImageResponse)
                .toList();
    }

    @Override
    @Transactional
    public ProductImageResponse addProductImage(UUID companyId, UUID productId, UUID ownerId, AddProductImageRequest request) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PRODUCTS);

        Product product = productRepository.findByIdAndCompanyIdWithLock(productId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        int currentCount = productImageRepository.countByProductId(productId);
        if (currentCount >= 5) {
            throw new BadRequestException("Product already has the maximum of 5 images");
        }

        ProductImage image = new ProductImage();
        image.setProduct(product);
        image.setImageUrl(request.getImageUrl());
        image.setDisplayOrder(currentCount);

        ProductImage saved = productImageRepository.save(image);

        if (currentCount == 0) {
            product.setThumbnailUrl(saved.getImageUrl());
            productRepository.save(product);
        }

        evictAfterCommit(() -> {
            singleFlightCache.evict("product:" + companyId + ":" + productId);
            singleFlightCache.evictByPattern("products:search:" + companyId + ":*");
            singleFlightCache.evictByPattern("products:batch:" + companyId + ":*");
        });
        return toImageResponse(saved);
    }

    @Override
    @Transactional
    public void deleteProductImage(UUID companyId, UUID productId, UUID imageId, UUID ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PRODUCTS);

        Product product = productRepository.findByIdAndCompanyId(productId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        ProductImage image = productImageRepository.findByIdAndProductId(imageId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found with id: " + imageId));

        productImageRepository.delete(image);

        List<ProductImage> remaining = productImageRepository.findAllByProductIdOrderByDisplayOrderAsc(productId);
        product.setThumbnailUrl(remaining.isEmpty() ? null : remaining.get(0).getImageUrl());
        productRepository.save(product);
        evictAfterCommit(() -> {
            singleFlightCache.evict("product:" + companyId + ":" + productId);
            singleFlightCache.evictByPattern("products:search:" + companyId + ":*");
            singleFlightCache.evictByPattern("products:batch:" + companyId + ":*");
        });
    }

    @Override
    @Transactional
    public List<ProductImageResponse> reorderProductImages(UUID companyId, UUID productId, UUID ownerId, ReorderProductImagesRequest request) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PRODUCTS);

        // Pessimistic write lock prevents concurrent reorder calls from overwriting each other.
        Product product = productRepository.findByIdAndCompanyIdWithLock(productId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        List<ProductImage> existing = productImageRepository.findAllByProductIdOrderByDisplayOrderAsc(productId);
        List<UUID> requestedIds = request.getImageIds();

        if (requestedIds.size() != existing.size()) {
            throw new BadRequestException("imageIds must contain all " + existing.size() + " image(s) for this product");
        }

        Set<UUID> existingIds = new HashSet<>();
        for (ProductImage img : existing) existingIds.add(img.getId());

        for (UUID id : requestedIds) {
            if (!existingIds.contains(id)) {
                throw new BadRequestException("Image id " + id + " does not belong to this product");
            }
        }

        java.util.Map<UUID, ProductImage> imageMap = new java.util.HashMap<>();
        for (ProductImage img : existing) imageMap.put(img.getId(), img);

        for (int i = 0; i < requestedIds.size(); i++) {
            imageMap.get(requestedIds.get(i)).setDisplayOrder(i);
        }

        productImageRepository.saveAll(existing);

        List<ProductImage> reordered = existing.stream()
                .sorted(java.util.Comparator.comparingInt(ProductImage::getDisplayOrder))
                .toList();

        if (!reordered.isEmpty()) {
            product.setThumbnailUrl(reordered.get(0).getImageUrl());
        }
        productRepository.save(product);

        evictAfterCommit(() -> {
            singleFlightCache.evict("product:" + companyId + ":" + productId);
            singleFlightCache.evictByPattern("products:search:" + companyId + ":*");
            singleFlightCache.evictByPattern("products:batch:" + companyId + ":*");
        });
        return reordered.stream()
                .map(this::toImageResponse)
                .toList();
    }

    // --- Options ---

    @Override
    @Transactional(readOnly = true)
    public List<ProductOptionResponse> getProductOptions(UUID companyId, UUID productId) {
        assertProductBelongsToCompany(companyId, productId);
        return productOptionRepository.findAllByProductIdOrderByPositionAsc(productId)
                .stream()
                .map(this::toOptionResponse)
                .toList();
    }

    @Override
    @Transactional
    public ProductOptionResponse addProductOption(UUID companyId, UUID productId, UUID ownerId, CreateProductOptionRequest request) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PRODUCTS);

        // Pessimistic write lock serializes concurrent option-add requests so the count
        // check and the insert are atomic with respect to other writers.
        Product product = productRepository.findByIdAndCompanyIdWithLock(productId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        int optionCount = productOptionRepository.countByProductId(productId);
        if (optionCount >= 3) {
            throw new BadRequestException("Products can have at most 3 option types");
        }

        ProductOption option = new ProductOption();
        option.setProduct(product);
        option.setName(request.getName());
        option.setPosition(optionCount);

        ProductOptionResponse result = toOptionResponse(productOptionRepository.save(option));
        evictAfterCommit(() -> {
            singleFlightCache.evict("product:" + companyId + ":" + productId);
            singleFlightCache.evictByPattern("products:search:" + companyId + ":*");
            singleFlightCache.evictByPattern("products:batch:" + companyId + ":*");
        });
        return result;
    }

    @Override
    @Transactional
    public ProductOptionResponse updateProductOption(UUID companyId, UUID productId, UUID optionId, UUID ownerId, UpdateProductOptionRequest request) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PRODUCTS);

        assertProductBelongsToCompany(companyId, productId);

        ProductOption option = productOptionRepository.findByIdAndProductId(optionId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Option not found with id: " + optionId));

        if (request.getName() != null) option.setName(request.getName());

        ProductOptionResponse result = toOptionResponse(productOptionRepository.save(option));
        evictAfterCommit(() -> {
            singleFlightCache.evict("product:" + companyId + ":" + productId);
            singleFlightCache.evictByPattern("products:search:" + companyId + ":*");
            singleFlightCache.evictByPattern("products:batch:" + companyId + ":*");
        });
        return result;
    }

    @Override
    @Transactional
    public void deleteProductOption(UUID companyId, UUID productId, UUID optionId, UUID ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PRODUCTS);

        assertProductBelongsToCompany(companyId, productId);

        ProductOption option = productOptionRepository.findByIdAndProductId(optionId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Option not found with id: " + optionId));

        productOptionRepository.delete(option);
        evictAfterCommit(() -> {
            singleFlightCache.evict("product:" + companyId + ":" + productId);
            singleFlightCache.evictByPattern("products:search:" + companyId + ":*");
            singleFlightCache.evictByPattern("products:batch:" + companyId + ":*");
        });
    }

    // --- Variants ---

    @Override
    @Transactional(readOnly = true)
    public List<ProductVariantResponse> getProductVariants(UUID companyId, UUID productId) {
        assertProductBelongsToCompany(companyId, productId);
        return productVariantRepository.findAllByProductIdOrderByDisplayOrderAsc(productId)
                .stream()
                .map(this::toVariantResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductVariantResponse getProductVariant(UUID companyId, UUID productId, UUID variantId) {
        assertProductBelongsToCompany(companyId, productId);
        ProductVariant variant = productVariantRepository.findByIdAndProductId(variantId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found with id: " + variantId));
        return toVariantResponse(variant);
    }

    @Override
    @Transactional
    public ProductVariantResponse createProductVariant(UUID companyId, UUID productId, UUID ownerId, CreateProductVariantRequest request) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PRODUCTS);

        Product product = productRepository.findByIdAndCompanyId(productId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        if (request.getSku() != null && !request.getSku().isBlank()
                && productVariantRepository.existsBySkuAndProductCompanyId(request.getSku(), companyId)) {
            throw new ConflictException("A variant with this SKU already exists in this company");
        }

        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSku(request.getSku());
        variant.setPrice(request.getPrice());
        variant.setCompareAtPrice(request.getCompareAtPrice());
        variant.setStock(request.getStock());
        variant.setLowStockThreshold(request.getLowStockThreshold());
        variant.setPurchasable(request.isPurchasable());
        variant.setOption1(request.getOption1());
        variant.setOption2(request.getOption2());
        variant.setOption3(request.getOption3());
        variant.setDisplayOrder(request.getDisplayOrder());

        ProductVariant savedVariant = productVariantRepository.save(variant);
        productChangeLogger.logVariantCreate(savedVariant, ChangeSource.USER);
        ProductVariantResponse result = toVariantResponse(savedVariant);
        evictAfterCommit(() -> {
            singleFlightCache.evict("product:" + companyId + ":" + productId);
            singleFlightCache.evictByPattern("products:search:" + companyId + ":*");
            singleFlightCache.evictByPattern("products:batch:" + companyId + ":*");
        });
        return result;
    }

    @Override
    @Transactional
    public ProductVariantResponse updateProductVariant(UUID companyId, UUID productId, UUID variantId, UUID ownerId, UpdateProductVariantRequest request) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PRODUCTS);

        assertProductBelongsToCompany(companyId, productId);

        ProductVariant variant = productVariantRepository.findByIdAndProductId(variantId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found with id: " + variantId));

        ProductVariant variantBefore = productChangeLogger.snapshot(variant);

        if (request.getSku() != null && !request.getSku().equals(variant.getSku())) {
            if (productVariantRepository.existsBySkuAndProductCompanyId(request.getSku(), companyId)) {
                throw new ConflictException("A variant with this SKU already exists in this company");
            }
            variant.setSku(request.getSku());
        }

        if (request.getPrice() != null) variant.setPrice(request.getPrice());
        if (request.getCompareAtPrice() != null) variant.setCompareAtPrice(request.getCompareAtPrice());
        if (request.getStock() != null) variant.setStock(request.getStock());
        if (request.getLowStockThreshold() != null) variant.setLowStockThreshold(request.getLowStockThreshold());
        if (request.getPurchasable() != null) variant.setPurchasable(request.getPurchasable());
        if (request.getPreorderEnabled() != null) variant.setPreorderEnabled(request.getPreorderEnabled());
        if (request.getPreorderExpectedDate() != null) variant.setPreorderExpectedDate(request.getPreorderExpectedDate());
        if (request.getOption1() != null) variant.setOption1(request.getOption1());
        if (request.getOption2() != null) variant.setOption2(request.getOption2());
        if (request.getOption3() != null) variant.setOption3(request.getOption3());
        if (request.getDisplayOrder() != null) variant.setDisplayOrder(request.getDisplayOrder());

        ProductVariant savedVariant = productVariantRepository.save(variant);
        productChangeLogger.logVariantUpdate(variantBefore, savedVariant, ChangeSource.USER, null);
        ProductVariantResponse result = toVariantResponse(savedVariant);
        evictAfterCommit(() -> {
            singleFlightCache.evict("product:" + companyId + ":" + productId);
            singleFlightCache.evictByPattern("products:search:" + companyId + ":*");
            singleFlightCache.evictByPattern("products:batch:" + companyId + ":*");
        });
        return result;
    }

    @Override
    @Transactional
    public void deleteProductVariant(UUID companyId, UUID productId, UUID variantId, UUID ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PRODUCTS);

        assertProductBelongsToCompany(companyId, productId);

        ProductVariant variant = productVariantRepository.findByIdAndProductId(variantId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found with id: " + variantId));

        productChangeLogger.logVariantDelete(variant);
        productVariantRepository.delete(variant);
        evictAfterCommit(() -> {
            singleFlightCache.evict("product:" + companyId + ":" + productId);
            singleFlightCache.evictByPattern("products:search:" + companyId + ":*");
            singleFlightCache.evictByPattern("products:batch:" + companyId + ":*");
        });
    }

    // --- Attributes ---

    @Override
    @Transactional(readOnly = true)
    public List<ProductAttributeResponse> getProductAttributes(UUID companyId, UUID productId) {
        assertProductBelongsToCompany(companyId, productId);
        return productAttributeRepository.findAllByProductIdOrderByDisplayOrderAsc(productId)
                .stream()
                .map(this::toAttrResponse)
                .toList();
    }

    @Override
    @Transactional
    public List<ProductAttributeResponse> setProductAttributes(UUID companyId, UUID productId, UUID ownerId, SetProductAttributesRequest request) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PRODUCTS);

        Product product = productRepository.findByIdAndCompanyId(productId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        productAttributeRepository.deleteAllByProductId(productId);

        List<ProductAttribute> attributes = request.getAttributes().stream()
                .map(item -> {
                    ProductAttribute attr = new ProductAttribute();
                    attr.setProduct(product);
                    attr.setName(item.getName());
                    attr.setValue(item.getValue());
                    attr.setDisplayOrder(item.getDisplayOrder());
                    return attr;
                })
                .toList();

        List<ProductAttributeResponse> result = productAttributeRepository.saveAll(attributes)
                .stream()
                .map(this::toAttrResponse)
                .toList();
        evictAfterCommit(() -> {
            singleFlightCache.evict("product:" + companyId + ":" + productId);
            singleFlightCache.evictByPattern("products:search:" + companyId + ":*");
            singleFlightCache.evictByPattern("products:batch:" + companyId + ":*");
        });
        return result;
    }

    // --- Marketplace catalog ---

    @Override
    @Transactional(readOnly = true)
    public CatalogSearchResponse searchMarketplaceCatalog(
            UUID marketplaceId, String q, String category, String brand,
            BigDecimal minPrice, BigDecimal maxPrice, Boolean featured, UUID vendorId,
            int page, int size, String sort, String direction) {

        if (!marketplaceProfileRepository.existsByCompanyId(marketplaceId)) {
            throw new ResourceNotFoundException("Marketplace not found");
        }
        if (page > 10_000) {
            throw new BadRequestException("Page number too large. Maximum page is 10,000.");
        }
        final int clampedSize = Math.min(size, 50);
        String cacheKey = String.format("marketplace:search:%s:%s:%s:%s:%s:%s:%s:%s:%d:%d:%s:%s",
                marketplaceId, q, category, brand, minPrice, maxPrice, featured,
                vendorId, page, clampedSize, sort, direction);
        return singleFlightCache.getOrLoad(cacheKey, cacheTtl, () -> {
        // When the shopper hasn't picked a specific sort, let function_score (pin + boost) drive
        // the order via _score. Only override with an explicit field sort when the caller asks
        // for one. The JPA fallback still needs a deterministic order, so use createdAt there.
        boolean explicitSort = sort != null && SORTABLE_FIELDS.contains(sort);
        String sortField = explicitSort ? sort : "createdAt";
        Sort.Direction sortDir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable esPageable = explicitSort
                ? PageRequest.of(page, clampedSize, Sort.by(sortDir, sortField))
                : PageRequest.of(page, clampedSize);
        Pageable pageable = PageRequest.of(page, clampedSize, Sort.by(sortDir, sortField));

        // --- Elasticsearch path ---
        try {
            final String marketplaceIdStr = marketplaceId.toString();
            final String vendorIdStr = vendorId != null ? vendorId.toString() : null;
            BoolQuery.Builder bq = new BoolQuery.Builder()
                    .filter(TermQuery.of(t -> t.field("marketplaceId").value(marketplaceIdStr))._toQuery())
                    .filter(TermQuery.of(t -> t.field("marketplaceListed").value(true))._toQuery())
                    .filter(TermQuery.of(t -> t.field("status").value("ACTIVE"))._toQuery());

            if (q != null && !q.isBlank()) {
                bq.must(MultiMatchQuery.of(mm -> mm
                        .fields("name^3", "description", "brand^2", "category", "tags", "vendorName")
                        .query(q)
                        .fuzziness("AUTO"))._toQuery());
            }
            if (category != null) bq.filter(TermQuery.of(t -> t.field("category").value(category))._toQuery());
            if (brand    != null) bq.filter(TermQuery.of(t -> t.field("brand").value(brand))._toQuery());
            if (featured != null) bq.filter(TermQuery.of(t -> t.field("featured").value(featured))._toQuery());
            if (vendorIdStr != null) bq.filter(TermQuery.of(t -> t.field("vendorId").value(vendorIdStr))._toQuery());
            if (minPrice != null || maxPrice != null) {
                final Double minVal = minPrice != null ? minPrice.doubleValue() : null;
                final Double maxVal = maxPrice != null ? maxPrice.doubleValue() : null;
                bq.filter(co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery.of(r -> r.number(n -> {
                    n.field("price");
                    if (minVal != null) n.gte(minVal);
                    if (maxVal != null) n.lte(maxVal);
                    return n;
                }))._toQuery());
            }

            NativeQuery esQuery = NativeQuery.builder()
                    .withQuery(applyMerchandisingScore(bq.build()._toQuery()))
                    .withPageable(esPageable)
                    .withAggregation("categories", Aggregation.of(a -> a.terms(t -> t.field("category").size(20).minDocCount(1))))
                    .withAggregation("brands", Aggregation.of(a -> a.terms(t -> t.field("brand").size(20).minDocCount(1))))
                    .withAggregation("price_ranges", Aggregation.of(a -> a.range(r -> r
                            .field("price")
                            .ranges(rb -> rb.to(25.0))
                            .ranges(rb -> rb.from(25.0).to(50.0))
                            .ranges(rb -> rb.from(50.0).to(100.0))
                            .ranges(rb -> rb.from(100.0).to(200.0))
                            .ranges(rb -> rb.from(200.0)))))
                    .build();

            SearchHits<ProductDocument> hits = elasticsearchOperations.search(esQuery, ProductDocument.class);
            List<UUID> ids = hits.stream().map(h -> h.getContent().getId()).toList();

            List<Product> products = productRepository.findAllByIdInAndMarketplaceId(ids, marketplaceId);
            Map<UUID, Product> productMap = products.stream().collect(Collectors.toMap(Product::getId, p -> p));

            Map<UUID, MarketplaceVendor> vendorMap = buildVendorMap(marketplaceId, products);
            Map<UUID, ActivePromotionSummary> promoMap = activePromotionLookupService.findForProducts(products);

            List<MarketplaceCatalogProductResponse> content = ids.stream()
                    .filter(productMap::containsKey)
                    .map(id -> toCatalogResponse(
                            productMap.get(id),
                            vendorMap.get(productMap.get(id).getCompany().getId()),
                            promoMap.get(id)))
                    .toList();

            SearchFacets facets = extractFacets(hits);
            return new CatalogSearchResponse(new PageImpl<>(content, pageable, hits.getTotalHits()), facets);

        } catch (Exception e) {
            log.warn("[CATALOG SEARCH] Elasticsearch unavailable: {}", e.getMessage());
            throw new ServiceUnavaliableException("Search is temporarily unavailable. Please try again shortly.");
        }
        }, new TypeReference<CatalogSearchResponse>() {});
    }

    @Override
    @Transactional(readOnly = true)
    public CatalogSearchResponse searchCompanyCatalog(
            UUID companyId, String q, String category, String brand,
            BigDecimal minPrice, BigDecimal maxPrice,
            int page, int size, String sort, String direction) {

        if (!companyRepository.existsById(companyId)) {
            throw new ResourceNotFoundException("Company not found");
        }
        final int clampedSize = Math.min(size, 50);
        String cacheKey = String.format("company:search:%s:%s:%s:%s:%s:%s:%d:%d:%s:%s",
                companyId, q, category, brand, minPrice, maxPrice,
                page, clampedSize, sort, direction);
        return singleFlightCache.getOrLoad(cacheKey, cacheTtl, () -> {
            boolean explicitSort = sort != null && SORTABLE_FIELDS.contains(sort);
            String sortField = explicitSort ? sort : "createdAt";
            Sort.Direction sortDir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
            Pageable esPageable = explicitSort
                    ? PageRequest.of(page, clampedSize, Sort.by(sortDir, sortField))
                    : PageRequest.of(page, clampedSize);
            Pageable pageable = PageRequest.of(page, clampedSize, Sort.by(sortDir, sortField));

            // --- Elasticsearch path ---
            try {
                final String companyIdStr2 = companyId.toString();
                BoolQuery.Builder bq = new BoolQuery.Builder()
                        .filter(TermQuery.of(t -> t.field("companyId").value(companyIdStr2))._toQuery())
                        .filter(TermQuery.of(t -> t.field("status").value("ACTIVE"))._toQuery());

                if (q != null && !q.isBlank()) {
                    bq.must(MultiMatchQuery.of(mm -> mm
                            .fields("name^3", "description", "brand^2", "category", "tags", "vendorName")
                            .query(q)
                            .fuzziness("AUTO"))._toQuery());
                }
                if (category != null) bq.filter(TermQuery.of(t -> t.field("category").value(category))._toQuery());
                if (brand    != null) bq.filter(TermQuery.of(t -> t.field("brand").value(brand))._toQuery());
                if (minPrice != null || maxPrice != null) {
                    final Double minVal = minPrice != null ? minPrice.doubleValue() : null;
                    final Double maxVal = maxPrice != null ? maxPrice.doubleValue() : null;
                    bq.filter(RangeQuery.of(r -> r.number(n -> {
                        n.field("price");
                        if (minVal != null) n.gte(minVal);
                        if (maxVal != null) n.lte(maxVal);
                        return n;
                    }))._toQuery());
                }

                NativeQuery esQuery = NativeQuery.builder()
                        .withQuery(applyMerchandisingScore(bq.build()._toQuery()))
                        .withPageable(esPageable)
                        .withAggregation("categories", Aggregation.of(a -> a.terms(t -> t.field("category").size(20))))
                        .withAggregation("brands", Aggregation.of(a -> a.terms(t -> t.field("brand").size(20))))
                        .withAggregation("price_ranges", Aggregation.of(a -> a.range(r -> r
                                .field("price")
                                .ranges(rb -> rb.to(25.0))
                                .ranges(rb -> rb.from(25.0).to(50.0))
                                .ranges(rb -> rb.from(50.0).to(100.0))
                                .ranges(rb -> rb.from(100.0).to(200.0))
                                .ranges(rb -> rb.from(200.0)))))
                        .build();

                SearchHits<ProductDocument> hits = elasticsearchOperations.search(esQuery, ProductDocument.class);
                List<UUID> ids = hits.stream().map(h -> h.getContent().getId()).toList();

                List<Product> products = productRepository.findAllByIdInAndCompanyId(ids, companyId);
                Map<UUID, Product> productMap = products.stream().collect(Collectors.toMap(Product::getId, p -> p));
                Map<UUID, ActivePromotionSummary> promoMap = activePromotionLookupService.findForProducts(products);

                List<MarketplaceCatalogProductResponse> content = ids.stream()
                        .filter(productMap::containsKey)
                        .map(id -> toCatalogResponse(productMap.get(id), null, promoMap.get(id)))
                        .toList();

                SearchFacets facets = extractFacets(hits);
                return new CatalogSearchResponse(new PageImpl<>(content, pageable, hits.getTotalHits()), facets);

            } catch (Exception e) {
                log.warn("[COMPANY CATALOG SEARCH] Elasticsearch unavailable: {}", e.getMessage());
                boolean hasFilters = (q != null && !q.isBlank())
                        || category != null || brand != null
                        || minPrice != null || maxPrice != null;
                if (hasFilters) {
                    throw new ServiceUnavaliableException("Search is temporarily unavailable. Please try again shortly.");
                }
                log.warn("[COMPANY CATALOG SEARCH] Falling back to unfiltered database listing");
            }

            // --- JPA fallback (unfiltered, ACTIVE-only) ---
            Page<Product> productPage = productRepository.findAllByCompanyId(companyId, pageable);
            List<Product> active = productPage.getContent().stream()
                    .filter(p -> p.getStatus() == ProductStatus.ACTIVE)
                    .toList();
            Map<UUID, ActivePromotionSummary> jpaPromoMap = activePromotionLookupService.findForProducts(active);
            List<MarketplaceCatalogProductResponse> content = active.stream()
                    .map(p -> toCatalogResponse(p, null, jpaPromoMap.get(p.getId())))
                    .toList();
            return new CatalogSearchResponse(
                    new PageImpl<>(content, pageable, productPage.getTotalElements()),
                    new SearchFacets(List.of(), List.of(), List.of()));
        }, new TypeReference<CatalogSearchResponse>() {});
    }

    @Override
    @Transactional(readOnly = true)
    public MarketplaceCatalogProductResponse getMarketplaceProduct(UUID marketplaceId, UUID productId) {
        String cacheKey = "marketplace:product:" + marketplaceId + ":" + productId;
        return singleFlightCache.getOrLoad(cacheKey, cacheTtl, () -> {
            Product product = productRepository.findByIdAndMarketplaceId(productId, marketplaceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found in this marketplace"));
            Map<UUID, MarketplaceVendor> vendorMap = buildVendorMap(marketplaceId, List.of(product));
            ActivePromotionSummary promo = activePromotionLookupService
                    .findForProducts(List.of(product))
                    .get(productId);
            return toCatalogResponse(product, vendorMap.get(product.getCompany().getId()), promo);
        }, MarketplaceCatalogProductResponse.class);
    }

    @Override
    @Transactional(readOnly = true)
    public VendorStorefrontResponse getVendorStorefront(UUID marketplaceId, UUID vendorId) {
        String cacheKey = "marketplace:storefront:" + marketplaceId + ":" + vendorId;
        return singleFlightCache.getOrLoad(cacheKey, cacheTtl, () -> {
            MarketplaceVendor vendor = marketplaceVendorRepository.findByIdAndMarketplaceId(vendorId, marketplaceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Vendor not found in this marketplace"));

            UUID vendorCompanyId = vendor.getVendorCompany().getId();
            List<Product> allVendorProducts = productRepository.findMarketplaceListed(marketplaceId).stream()
                    .filter(p -> p.getCompany().getId().equals(vendorCompanyId))
                    .toList();

            List<Product> featuredProducts = allVendorProducts.stream()
                    .filter(Product::isFeatured)
                    .limit(10)
                    .toList();
            Map<UUID, ActivePromotionSummary> storefrontPromoMap =
                    activePromotionLookupService.findForProducts(featuredProducts);
            List<MarketplaceCatalogProductResponse> featured = featuredProducts.stream()
                    .map(p -> toCatalogResponse(p, vendor, storefrontPromoMap.get(p.getId())))
                    .toList();

            return new VendorStorefrontResponse(
                    vendor.getId(),
                    marketplaceId,
                    vendor.getVendorCompany().getName(),
                    vendor.getVendorCompany().getDescription(),
                    vendor.getVendorCompany().getLogoUrl(),
                    vendor.getTier().name(),
                    vendor.getStatus().name(),
                    featured,
                    allVendorProducts.size()
            );
        }, VendorStorefrontResponse.class);
    }

    @Override
    @Transactional
    public ProductResponse updateMarketplaceListing(UUID companyId, UUID productId, UUID ownerId,
                                                     UpdateMarketplaceListingRequest request) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PRODUCTS);

        Product product = productRepository.findByIdAndCompanyId(productId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        boolean listing = Boolean.TRUE.equals(request.getListed());

        // Early exit: unlisting something that is already not listed is a no-op.
        if (!listing && product.getMarketplaceId() == null) {
            return toResponse(product);
        }

        UUID marketplaceToCheck = listing ? request.getMarketplaceId() : product.getMarketplaceId();
        if (marketplaceToCheck == null) {
            throw new BadRequestException("marketplaceId is required when listing a product");
        }
        if (!marketplaceVendorRepository.existsByMarketplaceIdAndVendorCompanyId(marketplaceToCheck, companyId)) {
            throw new ForbiddenException("Your company is not an approved vendor in this marketplace");
        }

        final UUID oldMarketplaceId = product.getMarketplaceId();
        product.setMarketplaceId(listing ? request.getMarketplaceId() : null);
        product.setMarketplaceListed(listing);

        Product saved = productRepository.save(product);
        eventPublisher.publishEvent(new ProductIndexEvent(saved, saved.getCompany().getId()));
        final UUID newMarketplaceId = saved.getMarketplaceId();
        evictAfterCommit(() -> {
            singleFlightCache.evict("product:" + companyId + ":" + productId);
            singleFlightCache.evictByPattern("products:search:" + companyId + ":*");
            singleFlightCache.evictByPattern("products:batch:" + companyId + ":*");
            if (oldMarketplaceId != null) {
                singleFlightCache.evict("marketplace:product:" + oldMarketplaceId + ":" + productId);
                singleFlightCache.evictByPattern("marketplace:search:" + oldMarketplaceId + ":*");
                singleFlightCache.evictByPattern("marketplace:storefront:" + oldMarketplaceId + ":*");
            }
            if (newMarketplaceId != null && !newMarketplaceId.equals(oldMarketplaceId)) {
                singleFlightCache.evict("marketplace:product:" + newMarketplaceId + ":" + productId);
                singleFlightCache.evictByPattern("marketplace:search:" + newMarketplaceId + ":*");
                singleFlightCache.evictByPattern("marketplace:storefront:" + newMarketplaceId + ":*");
            }
        });
        return toResponse(saved);
    }

    // --- Merchandising ---

    @Override
    @Transactional
    public ProductResponse updateProductMerchandising(UUID companyId, UUID productId, UUID ownerId,
                                                       UpdateProductMerchandisingRequest request) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PRODUCTS);

        Product product = productRepository.findByIdAndCompanyId(productId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        if (request.getPinnedUntil() != null && !request.getPinnedUntil().isAfter(Instant.now())) {
            throw new BadRequestException("pinnedUntil must be in the future");
        }
        // Clearing the pin window also clears the rank — keeping a rank without an active window
        // would leave a ghost ordering in the index after the next reindex.
        product.setBoostWeight(request.getBoostWeight());
        product.setPinnedUntil(request.getPinnedUntil());
        product.setPinnedRank(request.getPinnedUntil() == null ? null : request.getPinnedRank());

        Product saved = productRepository.save(product);
        eventPublisher.publishEvent(new ProductIndexEvent(saved, saved.getCompany().getId()));

        final UUID marketplaceId = saved.getMarketplaceId();
        evictAfterCommit(() -> {
            singleFlightCache.evict("product:" + companyId + ":" + productId);
            singleFlightCache.evictByPattern("products:search:" + companyId + ":*");
            singleFlightCache.evictByPattern("products:batch:" + companyId + ":*");
            if (marketplaceId != null) {
                singleFlightCache.evict("marketplace:product:" + marketplaceId + ":" + productId);
                singleFlightCache.evictByPattern("marketplace:search:" + marketplaceId + ":*");
                singleFlightCache.evictByPattern("marketplace:storefront:" + marketplaceId + ":*");
            }
        });
        return toResponse(saved);
    }

    // --- Helpers ---

    /**
     * Wraps {@code base} in a {@code function_score} so that merchandising signals influence the
     * relevance score:
     * <ul>
     *   <li>Pinned products (with {@code pinnedUntil} still in window) get a huge weight (1e4),
     *       which guarantees a higher {@code _score} than any non-pinned product on the same
     *       textual match. The {@code _last}-style ordering among pinned products is handled by
     *       a secondary {@code pinnedRank} sort in the caller — but in practice the weight is
     *       large enough that a pinned product always wins on {@code _score} alone.</li>
     *   <li>Boost weight (1–10) is folded in via {@code field_value_factor} with a {@code log1p}
     *       modifier so the impact saturates gracefully. Missing → 1 keeps unboosted products
     *       neutral.</li>
     * </ul>
     * Sort by a non-score field (price/name/stock) overrides this; that's intentional — when a
     * user explicitly sorts, they don't want pins moving things around.
     */
    private static Query applyMerchandisingScore(Query base) {
        Query pinnedFilter = RangeQuery.of(r -> r.date(d -> d.field("pinnedUntil").gt("now")))._toQuery();
        FunctionScore pinFn = FunctionScore.of(f -> f
                .filter(pinnedFilter)
                .weight(10000.0));
        FunctionScore boostFn = FunctionScore.of(f -> f
                .fieldValueFactor(FieldValueFactorScoreFunction.of(fvf -> fvf
                        .field("boostWeight")
                        .factor(1.0)
                        .modifier(FieldValueFactorModifier.Log1p)
                        .missing(1.0))));
        return FunctionScoreQuery.of(fs -> fs
                .query(base)
                .functions(pinFn, boostFn)
                .scoreMode(FunctionScoreMode.Multiply)
                .boostMode(FunctionBoostMode.Multiply))._toQuery();
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

    // -------------------------------------------------------------------------
    // Relationships
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<ProductRelationshipResponse> getProductRelationships(UUID companyId, UUID productId, ProductRelationshipType type) {
        assertProductBelongsToCompany(companyId, productId);
        List<ProductRelationship> rels = (type == null)
                ? productRelationshipRepository.findAllBySourceProductIdAndSourceProductCompanyId(productId, companyId)
                : productRelationshipRepository.findAllBySourceProductIdAndTypeAndSourceProductCompanyId(productId, type, companyId);
        return rels.stream().map(this::toRelationshipResponse).toList();
    }

    @Override
    @Transactional
    public ProductRelationshipResponse addProductRelationship(UUID companyId, UUID productId, UUID ownerId, AddProductRelationshipRequest request) {
        assertProductBelongsToCompany(companyId, productId);
        if (productId.equals(request.getTargetProductId())) {
            throw new BadRequestException("A product cannot have a relationship with itself");
        }
        Product target = productRepository.findByIdAndCompanyId(request.getTargetProductId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Target product not found with id: " + request.getTargetProductId()));
        if (productRelationshipRepository.existsBySourceProductIdAndTargetProductIdAndType(productId, target.getId(), request.getType())) {
            throw new ConflictException("Relationship of this type already exists between these products");
        }
        Product source = productRepository.findByIdAndCompanyId(productId, companyId).orElseThrow();
        ProductRelationship rel = new ProductRelationship();
        rel.setSourceProduct(source);
        rel.setTargetProduct(target);
        rel.setType(request.getType());
        rel.setNote(request.getNote());
        rel.setDisplayOrder(request.getDisplayOrder());
        return toRelationshipResponse(productRelationshipRepository.save(rel));
    }

    @Override
    @Transactional
    public void removeProductRelationship(UUID companyId, UUID productId, UUID targetProductId, ProductRelationshipType type, UUID ownerId) {
        assertProductBelongsToCompany(companyId, productId);
        if (!productRelationshipRepository.existsBySourceProductIdAndTargetProductIdAndType(productId, targetProductId, type)) {
            throw new ResourceNotFoundException("Relationship not found");
        }
        productRelationshipRepository.deleteBySourceProductIdAndTargetProductIdAndType(productId, targetProductId, type);
    }

    private ProductRelationshipResponse toRelationshipResponse(ProductRelationship rel) {
        Product target = rel.getTargetProduct();
        return new ProductRelationshipResponse(
                target.getId(),
                target.getName(),
                target.getSku(),
                target.getThumbnailUrl(),
                rel.getType(),
                rel.getNote(),
                rel.getDisplayOrder()
        );
    }

    // -------------------------------------------------------------------------
    // Similar products
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<SimilarProductResponse> getSimilarProducts(UUID companyId, UUID productId, int limit) {
        assertCompanyExists(companyId);
        int clampedLimit = Math.max(1, Math.min(limit, 20));
        String cacheKey = "products:similar:" + companyId + ":" + productId + ":" + clampedLimit;
        return singleFlightCache.getOrLoad(cacheKey, cacheTtlShort, () -> {
            Product source = productRepository.findByIdAndCompanyId(productId, companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

            Set<UUID> excludeIds = new HashSet<>();
            excludeIds.add(productId);
            List<SimilarProductResponse> results = new ArrayList<>();

            // 1. Explicit SIMILAR relationships
            List<ProductRelationship> manualRels = productRelationshipRepository
                    .findAllBySourceProductIdAndTypeAndSourceProductCompanyId(
                            productId, ProductRelationshipType.SIMILAR, companyId);

            if (!manualRels.isEmpty()) {
                List<UUID> manualTargetIds = manualRels.stream()
                        .map(r -> r.getTargetProduct().getId())
                        .toList();
                Map<UUID, Product> manualProductMap = productRepository
                        .findAllByIdInAndCompanyId(manualTargetIds, companyId)
                        .stream().collect(Collectors.toMap(Product::getId, p -> p));
                Map<UUID, double[]> manualRatings = buildRatingMap(manualTargetIds);

                for (ProductRelationship rel : manualRels) {
                    UUID tid = rel.getTargetProduct().getId();
                    if (manualProductMap.containsKey(tid)) {
                        results.add(toSimilarResponse(manualProductMap.get(tid), "MANUAL", manualRatings, List.of()));
                        excludeIds.add(tid);
                    }
                    if (results.size() >= clampedLimit) break;
                }
            }

            // 2. Pre-computed similarity rows
            int remaining = clampedLimit - results.size();
            if (remaining > 0) {
                List<ProductSimilarity> preRows = productSimilarityRepository
                        .findTop10ByIdSourceProductIdOrderByScoreDesc(productId);
                if (!preRows.isEmpty()) {
                    List<UUID> preIds = preRows.stream()
                            .map(r -> r.getId().getTargetProductId())
                            .filter(id -> !excludeIds.contains(id))
                            .limit(remaining)
                            .toList();
                    if (!preIds.isEmpty()) {
                        Map<UUID, ProductSimilarity> rowByTarget = preRows.stream()
                                .collect(Collectors.toMap(r -> r.getId().getTargetProductId(), r -> r));
                        List<Product> preProducts = productRepository.findAllByIdInAndCompanyId(preIds, companyId)
                                .stream()
                                .filter(p -> p.getStatus() == ProductStatus.ACTIVE)
                                .toList();
                        Map<UUID, double[]> preRatings = buildRatingMap(preIds);
                        for (Product p : preProducts) {
                            ProductSimilarity row = rowByTarget.get(p.getId());
                            List<String> reasons = row != null
                                    ? ProductSimilarityService.parseSignals(row.getMatchSignals())
                                            .stream().map(Enum::name).toList()
                                    : List.of();
                            results.add(toSimilarResponse(p, "AUTO", preRatings, reasons));
                            excludeIds.add(p.getId());
                        }
                    }
                }
            }

            // 3. On-demand ES auto-fill for remaining slots
            remaining = clampedLimit - results.size();
            if (remaining > 0) {
                List<UUID> autoIds = findAutoSimilarIds(source, remaining, excludeIds);
                if (!autoIds.isEmpty()) {
                    List<Product> autoProducts = productRepository.findAllByIdInAndCompanyId(autoIds, companyId);
                    Map<UUID, double[]> autoRatings = buildRatingMap(autoIds);
                    for (Product p : autoProducts) {
                        results.add(toSimilarResponse(p, "AUTO", autoRatings, List.of()));
                    }
                }
            }

            // 4. Featured fallback — only when all other steps yielded nothing
            if (results.isEmpty()) {
                List<Product> featured = productRepository
                        .findFeaturedByCompanyId(companyId, PageRequest.of(0, clampedLimit + 1))
                        .getContent()
                        .stream()
                        .filter(p -> !p.getId().equals(productId))
                        .limit(clampedLimit)
                        .toList();
                if (!featured.isEmpty()) {
                    List<UUID> featuredIds = featured.stream().map(Product::getId).toList();
                    Map<UUID, double[]> featuredRatings = buildRatingMap(featuredIds);
                    for (Product p : featured) {
                        results.add(toSimilarResponse(p, "FEATURED", featuredRatings, List.of()));
                    }
                }
            }

            return results;
        }, new TypeReference<List<SimilarProductResponse>>() {});
    }

    private List<UUID> findAutoSimilarIds(Product source, int remaining, Set<UUID> excludeIds) {
        String companyIdStr = source.getCompany().getId().toString();
        BoolQuery.Builder bq = new BoolQuery.Builder()
                .filter(TermQuery.of(t -> t.field("companyId").value(companyIdStr))._toQuery())
                .filter(TermQuery.of(t -> t.field("status").value(ProductStatus.ACTIVE.name()))._toQuery());

        if (source.getCategory() != null) {
            final String cat = source.getCategory();
            bq.filter(TermQuery.of(t -> t.field("category").value(cat))._toQuery());
        }
        if (source.getPrice() != null) {
            final double minP = source.getPrice().multiply(new BigDecimal("0.5")).doubleValue();
            final double maxP = source.getPrice().multiply(new BigDecimal("2.0")).doubleValue();
            bq.filter(RangeQuery.of(r -> r.number(n -> n.field("price").gte(minP).lte(maxP)))._toQuery());
        }

        int fetchSize = Math.min(remaining + excludeIds.size(), 50);
        NativeQuery esQuery = NativeQuery.builder()
                .withQuery(applyMerchandisingScore(bq.build()._toQuery()))
                .withPageable(PageRequest.of(0, fetchSize))
                .build();

        try {
            SearchHits<ProductDocument> hits = elasticsearchOperations.search(esQuery, ProductDocument.class);
            return hits.stream()
                    .map(h -> h.getContent().getId())
                    .filter(id -> !excludeIds.contains(id))
                    .limit(remaining)
                    .toList();
        } catch (Exception e) {
            log.warn("[SIMILAR] Elasticsearch unavailable, falling back to database: {}", e.getMessage());
            Pageable pageable = PageRequest.of(0, fetchSize);
            return productRepository.findAll(
                    ProductSpecification.withFilters(
                            source.getCompany().getId(), null, source.getCategory(), source.getBrand(),
                            null, null, null, ProductStatus.ACTIVE, true, null, null),
                    pageable)
                    .stream()
                    .map(Product::getId)
                    .filter(id -> !excludeIds.contains(id))
                    .limit(remaining)
                    .toList();
        }
    }

    private SimilarProductResponse toSimilarResponse(Product p, String source, Map<UUID, double[]> ratingMap,
                                                       List<String> matchReasons) {
        double[] stats = ratingMap.getOrDefault(p.getId(), new double[]{0.0, 0.0});
        Double avgRating = stats[1] > 0 ? stats[0] : null;
        Long reviewCount = (long) stats[1];
        return new SimilarProductResponse(
                p.getId(),
                p.getName(),
                p.getSku(),
                p.getThumbnailUrl(),
                p.getPrice(),
                p.getCompareAtPrice(),
                p.getCurrency(),
                p.getCategory(),
                p.getBrand(),
                avgRating,
                reviewCount,
                source,
                matchReasons != null ? matchReasons : List.of()
        );
    }

    private void assertCompanyExists(UUID companyId) {
        if (!companyRepository.existsById(companyId)) {
            throw new ResourceNotFoundException("Company not found with id: " + companyId);
        }
    }

    private void assertProductBelongsToCompany(UUID companyId, UUID productId) {
        assertCompanyExists(companyId);
        if (!productRepository.findByIdAndCompanyId(productId, companyId).isPresent()) {
            throw new ResourceNotFoundException("Product not found with id: " + productId);
        }
    }

    private ProductImageResponse toImageResponse(ProductImage img) {
        return new ProductImageResponse(img.getId(), img.getImageUrl(), img.getDisplayOrder(), img.getCreatedAt());
    }

    private ProductOptionResponse toOptionResponse(ProductOption opt) {
        return new ProductOptionResponse(opt.getId(), opt.getName(), opt.getPosition());
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

    private ProductAttributeResponse toAttrResponse(ProductAttribute attr) {
        return new ProductAttributeResponse(attr.getId(), attr.getName(), attr.getValue(), attr.getDisplayOrder());
    }

    private SearchFacets extractFacets(SearchHits<?> hits) {
        if (hits.getAggregations() == null) {
            return new SearchFacets(List.of(), List.of(), List.of());
        }
        try {
            ElasticsearchAggregations aggs = (ElasticsearchAggregations) hits.getAggregations();
            Map<String, ElasticsearchAggregation> aggMap = aggs.aggregationsAsMap();

            List<FacetBucket> categories = aggMap.containsKey("categories")
                    ? aggMap.get("categories").aggregation().getAggregate().sterms().buckets().array().stream()
                            .filter(b -> b.key().stringValue() != null && !b.key().stringValue().isBlank())
                            .map(b -> new FacetBucket(b.key().stringValue(), b.docCount()))
                            .toList()
                    : List.of();

            List<FacetBucket> brands = aggMap.containsKey("brands")
                    ? aggMap.get("brands").aggregation().getAggregate().sterms().buckets().array().stream()
                            .filter(b -> b.key().stringValue() != null && !b.key().stringValue().isBlank())
                            .map(b -> new FacetBucket(b.key().stringValue(), b.docCount()))
                            .toList()
                    : List.of();

            List<PriceRangeBucket> priceRanges = aggMap.containsKey("price_ranges")
                    ? aggMap.get("price_ranges").aggregation().getAggregate().range().buckets().array().stream()
                            .filter(b -> b.docCount() > 0)
                            .map(b -> new PriceRangeBucket(formatPriceLabel(b.from(), b.to()), b.from(), b.to(), b.docCount()))
                            .toList()
                    : List.of();

            return new SearchFacets(categories, brands, priceRanges);
        } catch (Exception e) {
            log.warn("[SEARCH] Failed to extract facets: {}", e.getMessage());
            return new SearchFacets(List.of(), List.of(), List.of());
        }
    }

    private static String formatPriceLabel(Double from, Double to) {
        if (from == null || from == 0.0) return "Under $" + to.intValue();
        if (to == null) return "$" + from.intValue() + "+";
        return "$" + from.intValue() + " – $" + to.intValue();
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

    private MarketplaceCatalogProductResponse toCatalogResponse(
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

    /**
     * Applies a target lifecycle status to {@code product}, enforcing the scheduling invariants.
     * The window of valid transitions is intentionally narrow because the scheduling worker, the
     * customer catalog filter, and the activation event hook all depend on these fields being
     * mutually consistent.
     *
     * @param product           the product being mutated
     * @param targetStatus      the status the caller wants to land on
     * @param scheduledPublishAt the schedule timestamp from the request (may be null)
     * @param isCreate          true on create — disables some "current state" checks
     */
    private void applyStatusTransition(
            Product product,
            ProductStatus targetStatus,
            Instant scheduledPublishAt,
            boolean isCreate) {
        ProductStatus previousStatus = isCreate ? null : product.getStatus();
        Instant now = Instant.now();

        if (targetStatus == ProductStatus.SCHEDULED) {
            if (previousStatus == ProductStatus.ARCHIVED
                    || previousStatus == ProductStatus.INACTIVE
                    || previousStatus == ProductStatus.DISCONTINUED) {
                throw new BadRequestException("Cannot schedule a product in this state. Restore it to draft first.");
            }
            Instant publishAt = scheduledPublishAt != null ? scheduledPublishAt : product.getScheduledPublishAt();
            if (publishAt == null) {
                throw new BadRequestException("scheduledPublishAt is required when status is SCHEDULED");
            }
            if (!publishAt.isAfter(now)) {
                throw new BadRequestException("scheduledPublishAt must be in the future");
            }
            product.setStatus(ProductStatus.SCHEDULED);
            product.setScheduledPublishAt(publishAt);
            return;
        }

        // Any non-SCHEDULED status clears the schedule field.
        product.setScheduledPublishAt(null);
        product.setStatus(targetStatus);

        if (targetStatus == ProductStatus.ACTIVE && product.getPublishedAt() == null) {
            product.setPublishedAt(now);
        }
    }

    private ProductResponse toResponse(Product product) {
        return toResponse(product, null);
    }

    private ProductResponse toResponse(Product product, ActivePromotionSummary activePromotion) {
        List<ProductImageResponse> images = product.getImages().stream()
                .map(this::toImageResponse)
                .toList();

        List<ProductOptionResponse> options = product.getOptions().stream()
                .map(this::toOptionResponse)
                .toList();

        List<ProductVariantResponse> variants = product.getVariants().stream()
                .map(this::toVariantResponse)
                .toList();

        List<ProductAttributeResponse> attributes = product.getAttributes().stream()
                .map(this::toAttrResponse)
                .toList();

        return toResponseWithRating(product, images, options, variants, attributes, List.of(), null, 0L, activePromotion);
    }

    private ProductResponse toResponseWithRating(
            Product product,
            List<ProductImageResponse> images,
            List<ProductOptionResponse> options,
            List<ProductVariantResponse> variants,
            List<ProductAttributeResponse> attributes,
            Double avgRating,
            Long reviewCount,
            ActivePromotionSummary activePromotion) {
        return toResponseWithRating(product, images, options, variants, attributes, List.of(), avgRating, reviewCount, activePromotion);
    }

    private ProductResponse toResponseWithRating(
            Product product,
            List<ProductImageResponse> images,
            List<ProductOptionResponse> options,
            List<ProductVariantResponse> variants,
            List<ProductAttributeResponse> attributes,
            List<ProductRelationshipResponse> relationships,
            Double avgRating,
            Long reviewCount,
            ActivePromotionSummary activePromotion) {
        return new ProductResponse(
                product.getId(),
                product.getCompany().getId(),
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
                options,
                variants,
                attributes,
                relationships,
                product.getStock(),
                product.getLowStockThreshold(),
                product.getWeight(),
                product.getWeightUnit(),
                product.getStatus().name(),
                product.getScheduledPublishAt(),
                product.getPublishedAt(),
                product.isFeatured(),
                product.isPurchasable(),
                product.isListed(),
                product.isPreorderEnabled(),
                product.getPreorderExpectedDate(),
                product.getBoostWeight(),
                product.getPinnedUntil(),
                product.getPinnedRank(),
                product.getCreatedAt(),
                product.getUpdatedAt(),
                avgRating,
                reviewCount,
                activePromotion
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> compareProducts(UUID companyId, List<UUID> ids) {
        if (ids == null || ids.size() < 2 || ids.size() > 4) {
            throw new BadRequestException("Comparison requires between 2 and 4 product IDs");
        }
        assertCompanyExists(companyId);
        String sortedIds = ids.stream().sorted().map(String::valueOf).collect(Collectors.joining(":"));
        String cacheKey = "products:compare:" + companyId + ":" + sortedIds;
        return singleFlightCache.getOrLoad(cacheKey, cacheTtlShort, () -> {
            List<Product> products = productRepository.findAllByIdInAndCompanyId(ids, companyId);
            if (products.isEmpty()) {
                throw new ResourceNotFoundException("No products found for the given IDs in this company");
            }
            List<UUID> foundIds = products.stream().map(Product::getId).toList();
            Map<UUID, double[]> ratingMap = buildRatingMap(foundIds);
            Map<UUID, ActivePromotionSummary> promoMap = activePromotionLookupService.findForProducts(products);

            return products.stream().map(p -> {
                double[] stats = ratingMap.getOrDefault(p.getId(), new double[]{0.0, 0.0});
                Double avgRating = stats[1] > 0 ? stats[0] : null;
                Long reviewCount = (long) stats[1];
                List<ProductImageResponse> images = p.getImages().stream().map(this::toImageResponse).toList();
                List<ProductOptionResponse> options = p.getOptions().stream().map(this::toOptionResponse).toList();
                List<ProductVariantResponse> variants = p.getVariants().stream().map(this::toVariantResponse).toList();
                List<ProductAttributeResponse> attributes = p.getAttributes().stream().map(this::toAttrResponse).toList();
                return toResponseWithRating(p, images, options, variants, attributes, avgRating, reviewCount, promoMap.get(p.getId()));
            }).toList();
        }, new TypeReference<List<ProductResponse>>() {});
    }

    private Map<UUID, double[]> buildRatingMap(List<UUID> productIds) {
        Map<UUID, double[]> map = new java.util.HashMap<>();
        try {
            List<Object[]> rows = productReviewRepository.findAverageRatingsByProductIds(productIds);
            for (Object[] row : rows) {
                UUID productId = (UUID) row[0];
                double avg = ((Number) row[1]).doubleValue();
                double count = ((Number) row[2]).doubleValue();
                map.put(productId, new double[]{avg, count});
            }
        } catch (Exception e) {
            log.warn("[COMPARE] Failed to load ratings: {}", e.getMessage());
        }
        return map;
    }

    // -------------------------------------------------------------------------
    // Versioning / change history
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<ProductHistoryEntryResponse> getProductHistory(
            UUID companyId, UUID productId, int page, int size) {
        productRepository.findByIdAndCompanyId(productId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        int clampedSize = Math.max(1, Math.min(size, 100));
        int safePage = Math.max(0, page);
        // Pull enough from each source to cover the requested page after a merge sort.
        int prefix = (safePage + 1) * clampedSize;
        Pageable prefixPage = PageRequest.of(0, prefix, Sort.by(Sort.Direction.DESC, "changedAt"));
        Pageable invPrefixPage = PageRequest.of(0, prefix, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<ProductChangeLog> changes = productChangeLogRepository
                .findAllByProductIdAndCompanyId(productId, companyId, prefixPage);
        Page<InventoryAdjustment> adjustments = inventoryAdjustmentRepository
                .findAllByProductIdAndProductCompanyId(productId, companyId, invPrefixPage);

        List<ProductHistoryEntryResponse> merged = new java.util.ArrayList<>(
                changes.getNumberOfElements() + adjustments.getNumberOfElements());
        for (ProductChangeLog c : changes.getContent()) merged.add(toHistoryEntry(c));
        for (InventoryAdjustment a : adjustments.getContent()) merged.add(toHistoryEntry(a));
        merged.sort((x, y) -> y.getOccurredAt().compareTo(x.getOccurredAt()));

        long total = changes.getTotalElements() + adjustments.getTotalElements();
        int from = Math.min(safePage * clampedSize, merged.size());
        int to = Math.min(from + clampedSize, merged.size());
        List<ProductHistoryEntryResponse> pageContent = merged.subList(from, to);
        Page<ProductHistoryEntryResponse> springPage = new PageImpl<>(
                pageContent, PageRequest.of(safePage, clampedSize), total);
        return new PagedResponse<>(springPage);
    }

    @Override
    @Transactional
    public ProductResponse revertProductChanges(
            UUID companyId, UUID productId, UUID ownerId, RevertProductChangesRequest request) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PRODUCTS);

        Product product = productRepository.findByIdAndCompanyId(productId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        if (request.getExpectedVersion() != null
                && !request.getExpectedVersion().equals(product.getVersion())) {
            throw new ConflictException(
                    "Product has been modified since you loaded history (expected version "
                            + request.getExpectedVersion() + ", current " + product.getVersion() + ")");
        }

        List<ProductChangeLog> entries = productChangeLogRepository
                .findAllByIdInAndCompanyId(request.getLogEntryIds(), companyId);
        if (entries.size() != request.getLogEntryIds().size()) {
            throw new ResourceNotFoundException("One or more log entries were not found in this company");
        }
        for (ProductChangeLog e : entries) {
            if (!e.getProduct().getId().equals(productId)) {
                throw new BadRequestException("Log entry " + e.getId() + " does not belong to product " + productId);
            }
            if ("stock".equals(e.getFieldName())) {
                throw new BadRequestException(
                        "Stock changes cannot be reverted here — use the inventory adjustment endpoint");
            }
        }

        // Group entries by variant (null = parent product). Within each group, keep only the
        // newest entry per field — that is the value the user wants to undo, and its oldValue
        // is the target state.
        Map<UUID, Map<String, ProductChangeLog>> byVariant = new java.util.HashMap<>();
        for (ProductChangeLog e : entries) {
            UUID variantId = e.getVariant() == null ? null : e.getVariant().getId();
            byVariant.computeIfAbsent(variantId, k -> new java.util.HashMap<>())
                    .merge(e.getFieldName(), e, (oldE, newE) ->
                            newE.getChangedAt().isAfter(oldE.getChangedAt()) ? newE : oldE);
        }

        Map<String, ProductChangeLog> productFieldRevisions = byVariant.remove(null);
        if (productFieldRevisions != null && !productFieldRevisions.isEmpty()) {
            Product before = productChangeLogger.snapshot(product);
            for (Map.Entry<String, ProductChangeLog> entry : productFieldRevisions.entrySet()) {
                applyProductField(product, entry.getKey(), entry.getValue().getOldValue());
            }
            Product saved = productRepository.save(product);
            UUID lastEntryId = productFieldRevisions.values().stream()
                    .map(ProductChangeLog::getId)
                    .max(UUID::compareTo)
                    .orElse(null);
            productChangeLogger.logUpdate(before, saved, ChangeSource.REVERT, lastEntryId);
            product = saved;
        }

        for (Map.Entry<UUID, Map<String, ProductChangeLog>> v : byVariant.entrySet()) {
            ProductVariant variant = productVariantRepository.findByIdAndProductId(v.getKey(), productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Variant not found: " + v.getKey()));
            ProductVariant beforeVariant = productChangeLogger.snapshot(variant);
            for (Map.Entry<String, ProductChangeLog> entry : v.getValue().entrySet()) {
                applyVariantField(variant, entry.getKey(), entry.getValue().getOldValue());
            }
            ProductVariant savedVariant = productVariantRepository.save(variant);
            UUID lastEntryId = v.getValue().values().stream()
                    .map(ProductChangeLog::getId)
                    .max(UUID::compareTo)
                    .orElse(null);
            productChangeLogger.logVariantUpdate(beforeVariant, savedVariant, ChangeSource.REVERT, lastEntryId);
        }

        eventPublisher.publishEvent(new ProductIndexEvent(product, product.getCompany().getId()));
        final UUID productIdF = productId;
        final UUID marketplaceId = product.getMarketplaceId();
        evictAfterCommit(() -> {
            singleFlightCache.evict("product:" + companyId + ":" + productIdF);
            singleFlightCache.evictByPattern("products:search:" + companyId + ":*");
            singleFlightCache.evictByPattern("products:batch:" + companyId + ":*");
            if (marketplaceId != null) {
                singleFlightCache.evict("marketplace:product:" + marketplaceId + ":" + productIdF);
                singleFlightCache.evictByPattern("marketplace:search:" + marketplaceId + ":*");
                singleFlightCache.evictByPattern("marketplace:storefront:" + marketplaceId + ":*");
            }
        });
        return toResponse(product);
    }

    private ProductHistoryEntryResponse toHistoryEntry(ProductChangeLog c) {
        UUID actorId = c.getChangedBy() == null ? null : c.getChangedBy().getId();
        return new ProductHistoryEntryResponse(
                ProductHistoryEntryResponse.Kind.FIELD_CHANGE,
                c.getId(),
                c.getProduct().getId(),
                c.getVariant() == null ? null : c.getVariant().getId(),
                actorId,
                resolveActorRole(c.getProduct().getCompany().getId(), actorId),
                c.getChangedAt(),
                c.getFieldName(),
                c.getOldValue(),
                c.getNewValue(),
                c.getChangeType().name(),
                c.getSource().name(),
                c.getRevertedFromLogId(),
                null, null, null, null, null, null);
    }

    private ProductHistoryEntryResponse toHistoryEntry(InventoryAdjustment a) {
        UUID actorId = a.getAdjustedBy() == null ? null : a.getAdjustedBy().getId();
        return new ProductHistoryEntryResponse(
                ProductHistoryEntryResponse.Kind.INVENTORY_ADJUSTMENT,
                a.getId(),
                a.getProduct().getId(),
                a.getVariant() == null ? null : a.getVariant().getId(),
                actorId,
                resolveActorRole(a.getProduct().getCompany().getId(), actorId),
                a.getCreatedAt(),
                null, null, null, null, null, null,
                a.getDelta(),
                a.getPreviousStock(),
                a.getNewStock(),
                a.getReason() == null ? null : a.getReason().name(),
                a.getNote(),
                a.getOrderId());
    }

    private backend.models.enums.CompanyRole resolveActorRole(UUID companyId, UUID actorId) {
        if (actorId == null) return null;
        return companyAccessService.resolveRole(companyId, actorId).orElse(null);
    }

    private void applyProductField(Product p, String field, String value) {
        switch (field) {
            case "name" -> p.setName(value);
            case "description" -> p.setDescription(value);
            case "sku" -> p.setSku(value);
            case "price" -> p.setPrice(value == null ? null : new BigDecimal(value));
            case "compareAtPrice" -> p.setCompareAtPrice(value == null ? null : new BigDecimal(value));
            case "currency" -> p.setCurrency(value);
            case "category" -> p.setCategory(value);
            case "brand" -> p.setBrand(value);
            case "tags" -> p.setTags(value);
            case "thumbnailUrl" -> p.setThumbnailUrl(value);
            case "weight" -> p.setWeight(value == null ? null : new BigDecimal(value));
            case "weightUnit" -> p.setWeightUnit(value);
            case "status" -> p.setStatus(value == null ? null : ProductStatus.valueOf(value));
            case "scheduledPublishAt" -> p.setScheduledPublishAt(value == null ? null : Instant.parse(value));
            case "featured" -> p.setFeatured(Boolean.parseBoolean(value));
            case "purchasable" -> p.setPurchasable(Boolean.parseBoolean(value));
            case "listed" -> p.setListed(Boolean.parseBoolean(value));
            case "backorderEnabled" -> p.setBackorderEnabled(Boolean.parseBoolean(value));
            case "preorderEnabled" -> p.setPreorderEnabled(Boolean.parseBoolean(value));
            case "preorderExpectedDate" -> p.setPreorderExpectedDate(value == null ? null : Instant.parse(value));
            case "subscribable" -> p.setSubscribable(Boolean.parseBoolean(value));
            case "subscriptionIntervals" -> p.setSubscriptionIntervals(value);
            case "subscriptionDiscountPercent" -> p.setSubscriptionDiscountPercent(value == null ? null : new BigDecimal(value));
            case "boostWeight" -> p.setBoostWeight(value == null ? null : Integer.parseInt(value));
            case "pinnedUntil" -> p.setPinnedUntil(value == null ? null : Instant.parse(value));
            case "pinnedRank" -> p.setPinnedRank(value == null ? null : Integer.parseInt(value));
            case "lowStockThreshold" -> p.setLowStockThreshold(value == null ? null : Integer.parseInt(value));
            case "lowStockThresholdPercent" -> p.setLowStockThresholdPercent(value == null ? null : Integer.parseInt(value));
            case "maxStock" -> p.setMaxStock(value == null ? null : Integer.parseInt(value));
            case "autoRestockEnabled" -> p.setAutoRestockEnabled(Boolean.parseBoolean(value));
            case "autoRestockQty" -> p.setAutoRestockQty(value == null ? null : Integer.parseInt(value));
            case "marketplaceListed" -> p.setMarketplaceListed(Boolean.parseBoolean(value));
            default -> throw new BadRequestException("Field '" + field + "' is not revertable");
        }
    }

    private void applyVariantField(ProductVariant v, String field, String value) {
        switch (field) {
            case "sku" -> v.setSku(value);
            case "price" -> v.setPrice(value == null ? null : new BigDecimal(value));
            case "compareAtPrice" -> v.setCompareAtPrice(value == null ? null : new BigDecimal(value));
            case "lowStockThreshold" -> v.setLowStockThreshold(value == null ? null : Integer.parseInt(value));
            case "lowStockThresholdPercent" -> v.setLowStockThresholdPercent(value == null ? null : Integer.parseInt(value));
            case "maxStock" -> v.setMaxStock(value == null ? null : Integer.parseInt(value));
            case "autoRestockEnabled" -> v.setAutoRestockEnabled(Boolean.parseBoolean(value));
            case "autoRestockQty" -> v.setAutoRestockQty(value == null ? null : Integer.parseInt(value));
            case "purchasable" -> v.setPurchasable(Boolean.parseBoolean(value));
            case "backorderEnabled" -> v.setBackorderEnabled(Boolean.parseBoolean(value));
            case "preorderEnabled" -> v.setPreorderEnabled(Boolean.parseBoolean(value));
            case "preorderExpectedDate" -> v.setPreorderExpectedDate(value == null ? null : Instant.parse(value));
            case "option1" -> v.setOption1(value);
            case "option2" -> v.setOption2(value);
            case "option3" -> v.setOption3(value);
            case "displayOrder" -> v.setDisplayOrder(value == null ? 0 : Integer.parseInt(value));
            default -> throw new BadRequestException("Variant field '" + field + "' is not revertable");
        }
    }
}
