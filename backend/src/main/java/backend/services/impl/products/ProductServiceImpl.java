package backend.services.impl.products;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
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
import backend.dtos.requests.product.AddProductImageRequest;
import backend.dtos.requests.product.BatchCreateProductsRequest;
import backend.dtos.requests.product.BatchDeleteProductsRequest;
import backend.dtos.requests.product.CreateProductOptionRequest;
import backend.dtos.requests.product.CreateProductRequest;
import backend.dtos.requests.product.CreateProductVariantRequest;
import backend.dtos.requests.product.ReorderProductImagesRequest;
import backend.dtos.requests.product.SetProductAttributesRequest;
import backend.dtos.requests.product.UpdateProductOptionRequest;
import backend.dtos.requests.product.UpdateProductRequest;
import backend.dtos.requests.product.UpdateProductVariantRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.product.ProductAttributeResponse;
import backend.dtos.responses.product.ProductImageResponse;
import backend.dtos.responses.product.ProductOptionResponse;
import backend.dtos.responses.product.ProductResponse;
import backend.dtos.responses.product.ProductVariantResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.exceptions.http.ServiceUnavaliableException;
import backend.models.core.Company;
import backend.models.core.Product;
import backend.models.core.ProductAttribute;
import backend.models.core.ProductImage;
import backend.models.core.ProductOption;
import backend.models.core.ProductVariant;
import backend.models.enums.ProductStatus;
import backend.dtos.requests.product.UpdateMarketplaceListingRequest;
import backend.dtos.responses.product.MarketplaceCatalogProductResponse;
import backend.dtos.responses.product.VendorStorefrontResponse;
import backend.models.core.MarketplaceVendor;
import backend.repositories.BundleRepository;
import backend.repositories.CompanyRepository;
import backend.repositories.MarketplaceProfileRepository;
import backend.repositories.MarketplaceVendorRepository;
import backend.repositories.ProductAttributeRepository;
import backend.repositories.ProductImageRepository;
import backend.repositories.ProductOptionRepository;
import backend.repositories.ProductRepository;
import backend.repositories.ProductReviewRepository;
import backend.repositories.ProductVariantRepository;
import backend.repositories.PromotionRuleRepository;
import backend.repositories.specifications.ProductSpecification;
import backend.services.intf.products.ProductService;
import org.springframework.context.ApplicationEventPublisher;

import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import backend.services.impl.SingleFlightCache;

import java.math.BigDecimal;
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
    private final PromotionRuleRepository promotionRuleRepository;
    private final MarketplaceProfileRepository marketplaceProfileRepository;
    private final MarketplaceVendorRepository marketplaceVendorRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ElasticsearchOperations elasticsearchOperations;
    private final SingleFlightCache singleFlightCache;
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
            PromotionRuleRepository promotionRuleRepository,
            MarketplaceProfileRepository marketplaceProfileRepository,
            MarketplaceVendorRepository marketplaceVendorRepository,
            ApplicationEventPublisher eventPublisher,
            ElasticsearchOperations elasticsearchOperations,
            SingleFlightCache singleFlightCache,
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
        this.promotionRuleRepository = promotionRuleRepository;
        this.marketplaceProfileRepository = marketplaceProfileRepository;
        this.marketplaceVendorRepository = marketplaceVendorRepository;
        this.eventPublisher = eventPublisher;
        this.elasticsearchOperations = elasticsearchOperations;
        this.singleFlightCache = singleFlightCache;
        this.cacheTtl = cacheTtl;
        this.cacheTtlShort = cacheTtlShort;
    }

    @Override
    public PagedResponse<ProductResponse> searchProducts(
            long companyId,
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
        final int clampedSize = Math.min(size, 50);
        String cacheKey = String.format("products:search:%d:%s:%s:%s:%s:%s:%s:%s:%s:%s:%s:%d:%d:%s:%s",
                companyId, q, category, brand, minPrice, maxPrice, featured,
                status, listed, discountCategory, hasDiscount, page, clampedSize, sort, direction);
        return singleFlightCache.getOrLoad(cacheKey, cacheTtl, () -> {
            String sortField = (sort != null && SORTABLE_FIELDS.contains(sort)) ? sort : "createdAt";
            Sort.Direction sortDir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
            Pageable pageable = PageRequest.of(page, clampedSize, Sort.by(sortDir, sortField));

            // --- Elasticsearch path ---
            try {
                BoolQuery.Builder bq = new BoolQuery.Builder()
                        .filter(TermQuery.of(t -> t.field("companyId").value(companyId))._toQuery());

                if (q != null && !q.isBlank()) {
                    bq.must(MultiMatchQuery.of(mm -> mm
                            .fields("name^3", "description", "brand^2", "category", "tags")
                            .query(q))._toQuery());
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
                        .withQuery(bq.build()._toQuery())
                        .withPageable(pageable)
                        .build();

                SearchHits<ProductDocument> hits = elasticsearchOperations.search(esQuery, ProductDocument.class);
                List<Long> ids = hits.stream().map(h -> h.getContent().getId()).toList();

                Map<Long, Product> productMap = productRepository
                        .findAllByIdInAndCompanyId(ids, companyId)
                        .stream()
                        .collect(Collectors.toMap(Product::getId, p -> p));

                List<ProductResponse> content = ids.stream()
                        .filter(productMap::containsKey)
                        .map(id -> toResponse(productMap.get(id)))
                        .toList();

                return new PagedResponse<>(new PageImpl<>(content, pageable, hits.getTotalHits()));

            } catch (Exception e) {
                log.warn("[SEARCH] Elasticsearch unavailable, falling back to database: {}", e.getMessage());
            }

            // --- JPA fallback ---
            return new PagedResponse<>(
                    productRepository
                            .findAll(ProductSpecification.withFilters(companyId, q, category, brand, minPrice, maxPrice, featured, status, listed, discountCategory, hasDiscount), pageable)
                            .map(this::toResponse)
            );
        }, new TypeReference<PagedResponse<ProductResponse>>() {});
    }

    @Override
    public ProductResponse getProduct(long companyId, long productId) {
        assertCompanyExists(companyId);
        String cacheKey = "product:" + companyId + ":" + productId;
        return singleFlightCache.getOrLoad(cacheKey, cacheTtl, () -> {
            Product product = productRepository.findByIdAndCompanyId(productId, companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));
            return toResponse(product);
        }, ProductResponse.class);
    }

    @Override
    public List<ProductResponse> getProductsByIds(long companyId, List<Long> ids) {
        assertCompanyExists(companyId);
        String sortedIds = ids.stream().sorted().map(String::valueOf).collect(Collectors.joining(":"));
        String cacheKey = "products:batch:" + companyId + ":" + sortedIds;
        return singleFlightCache.getOrLoad(cacheKey, cacheTtlShort, () ->
                productRepository.findAllByIdInAndCompanyId(ids, companyId)
                        .stream()
                        .map(this::toResponse)
                        .toList(),
                new TypeReference<List<ProductResponse>>() {});
    }

    @Override
    public ProductResponse createProduct(long companyId, long ownerId, CreateProductRequest request) {
        Company company = companyRepository.findByIdAndOwnerId(companyId, ownerId)
                .orElseThrow(() -> new ForbiddenException("You do not own this company"));

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
        product.setStatus(ProductStatus.DRAFT);

        Product saved = productRepository.save(product);
        eventPublisher.publishEvent(new ProductIndexEvent(saved, saved.getCompany().getId()));
        evictAfterCommit(() -> {
            singleFlightCache.evictByPattern("products:search:" + companyId + ":*");
            singleFlightCache.evictByPattern("products:batch:" + companyId + ":*");
        });
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ProductResponse updateProduct(long companyId, long productId, long ownerId, UpdateProductRequest request) {
        companyRepository.findByIdAndOwnerId(companyId, ownerId)
                .orElseThrow(() -> new ForbiddenException("You do not own this company"));

        Product product = productRepository.findByIdAndCompanyId(productId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

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
        if (request.getStatus() != null) product.setStatus(request.getStatus());
        if (request.getFeatured() != null) product.setFeatured(request.getFeatured());
        if (request.getPurchasable() != null) product.setPurchasable(request.getPurchasable());
        if (request.getListed() != null) product.setListed(request.getListed());

        boolean activating = request.getStatus() == ProductStatus.ACTIVE && originalStatus != ProductStatus.ACTIVE;
        boolean listing    = Boolean.TRUE.equals(request.getListed()) && !originalListed;

        if ((activating || listing) && productImageRepository.countByProductId(productId) < 1) {
            throw new BadRequestException("Product must have at least one image before it can be made active or listed");
        }

        Product saved = productRepository.save(product);
        eventPublisher.publishEvent(new ProductIndexEvent(saved, saved.getCompany().getId()));
        final Long marketplaceId = saved.getMarketplaceId();
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
    public void deleteProduct(long companyId, long productId, long ownerId) {
        companyRepository.findByIdAndOwnerId(companyId, ownerId)
                .orElseThrow(() -> new ForbiddenException("You do not own this company"));

        Product product = productRepository.findByIdAndCompanyId(productId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        if (bundleRepository.existsByItemsProductId(productId)) {
            throw new ConflictException("Product is part of one or more bundles. Remove it from all bundles before deleting.");
        }

        final Long marketplaceId = product.getMarketplaceId();
        promotionRuleRepository.removeProductFromAllRules(productId);
        productRepository.delete(product);
        eventPublisher.publishEvent(new ProductRemoveEvent(productId, marketplaceId));
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
    public List<ProductResponse> batchCreateProducts(long companyId, long ownerId, BatchCreateProductsRequest request) {
        Company company = companyRepository.findByIdAndOwnerId(companyId, ownerId)
                .orElseThrow(() -> new ForbiddenException("You do not own this company"));

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
            eventPublisher.publishEvent(new ProductIndexEvent(saved, companyId));
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
    public void batchDeleteProducts(long companyId, long ownerId, BatchDeleteProductsRequest request) {
        companyRepository.findByIdAndOwnerId(companyId, ownerId)
                .orElseThrow(() -> new ForbiddenException("You do not own this company"));

        List<Product> products = productRepository.findAllByIdInAndCompanyId(request.getIds(), companyId);

        if (products.size() != request.getIds().size()) {
            throw new ResourceNotFoundException("One or more products were not found in this company");
        }

        promotionRuleRepository.removeProductsFromAllRules(request.getIds());
        productRepository.deleteAll(products);
        for (Product p : products) {
            eventPublisher.publishEvent(new ProductRemoveEvent(p.getId(), p.getMarketplaceId()));
        }
        final List<Long> deletedIds = products.stream().map(Product::getId).toList();
        final List<Long> affectedMarketplaces = products.stream()
                .map(Product::getMarketplaceId).filter(Objects::nonNull).distinct().toList();
        evictAfterCommit(() -> {
            for (Long id : deletedIds) singleFlightCache.evict("product:" + companyId + ":" + id);
            singleFlightCache.evictByPattern("products:search:" + companyId + ":*");
            singleFlightCache.evictByPattern("products:batch:" + companyId + ":*");
            for (Long mpId : affectedMarketplaces) {
                singleFlightCache.evictByPattern("marketplace:search:" + mpId + ":*");
                singleFlightCache.evictByPattern("marketplace:storefront:" + mpId + ":*");
            }
        });
    }

    // --- Images ---

    @Override
    public List<ProductImageResponse> getProductImages(long companyId, long productId) {
        productRepository.findByIdAndCompanyId(productId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        return productImageRepository.findAllByProductIdOrderByDisplayOrderAsc(productId)
                .stream()
                .map(this::toImageResponse)
                .toList();
    }

    @Override
    @Transactional
    public ProductImageResponse addProductImage(long companyId, long productId, long ownerId, AddProductImageRequest request) {
        companyRepository.findByIdAndOwnerId(companyId, ownerId)
                .orElseThrow(() -> new ForbiddenException("You do not own this company"));

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
            singleFlightCache.evictByPattern("products:batch:" + companyId + ":*");
        });
        return toImageResponse(saved);
    }

    @Override
    @Transactional
    public void deleteProductImage(long companyId, long productId, long imageId, long ownerId) {
        companyRepository.findByIdAndOwnerId(companyId, ownerId)
                .orElseThrow(() -> new ForbiddenException("You do not own this company"));

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
            singleFlightCache.evictByPattern("products:batch:" + companyId + ":*");
        });
    }

    @Override
    @Transactional
    public List<ProductImageResponse> reorderProductImages(long companyId, long productId, long ownerId, ReorderProductImagesRequest request) {
        companyRepository.findByIdAndOwnerId(companyId, ownerId)
                .orElseThrow(() -> new ForbiddenException("You do not own this company"));

        // Pessimistic write lock prevents concurrent reorder calls from overwriting each other.
        Product product = productRepository.findByIdAndCompanyIdWithLock(productId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        List<ProductImage> existing = productImageRepository.findAllByProductIdOrderByDisplayOrderAsc(productId);
        List<Long> requestedIds = request.getImageIds();

        if (requestedIds.size() != existing.size()) {
            throw new BadRequestException("imageIds must contain all " + existing.size() + " image(s) for this product");
        }

        Set<Long> existingIds = new HashSet<>();
        for (ProductImage img : existing) existingIds.add(img.getId());

        for (Long id : requestedIds) {
            if (!existingIds.contains(id)) {
                throw new BadRequestException("Image id " + id + " does not belong to this product");
            }
        }

        java.util.Map<Long, ProductImage> imageMap = new java.util.HashMap<>();
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
            singleFlightCache.evictByPattern("products:batch:" + companyId + ":*");
        });
        return reordered.stream()
                .map(this::toImageResponse)
                .toList();
    }

    // --- Options ---

    @Override
    public List<ProductOptionResponse> getProductOptions(long companyId, long productId) {
        assertProductBelongsToCompany(companyId, productId);
        return productOptionRepository.findAllByProductIdOrderByPositionAsc(productId)
                .stream()
                .map(this::toOptionResponse)
                .toList();
    }

    @Override
    @Transactional
    public ProductOptionResponse addProductOption(long companyId, long productId, long ownerId, CreateProductOptionRequest request) {
        companyRepository.findByIdAndOwnerId(companyId, ownerId)
                .orElseThrow(() -> new ForbiddenException("You do not own this company"));

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
            singleFlightCache.evictByPattern("products:batch:" + companyId + ":*");
        });
        return result;
    }

    @Override
    @Transactional
    public ProductOptionResponse updateProductOption(long companyId, long productId, long optionId, long ownerId, UpdateProductOptionRequest request) {
        companyRepository.findByIdAndOwnerId(companyId, ownerId)
                .orElseThrow(() -> new ForbiddenException("You do not own this company"));

        assertProductBelongsToCompany(companyId, productId);

        ProductOption option = productOptionRepository.findByIdAndProductId(optionId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Option not found with id: " + optionId));

        if (request.getName() != null) option.setName(request.getName());

        ProductOptionResponse result = toOptionResponse(productOptionRepository.save(option));
        evictAfterCommit(() -> {
            singleFlightCache.evict("product:" + companyId + ":" + productId);
            singleFlightCache.evictByPattern("products:batch:" + companyId + ":*");
        });
        return result;
    }

    @Override
    @Transactional
    public void deleteProductOption(long companyId, long productId, long optionId, long ownerId) {
        companyRepository.findByIdAndOwnerId(companyId, ownerId)
                .orElseThrow(() -> new ForbiddenException("You do not own this company"));

        assertProductBelongsToCompany(companyId, productId);

        ProductOption option = productOptionRepository.findByIdAndProductId(optionId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Option not found with id: " + optionId));

        productOptionRepository.delete(option);
        evictAfterCommit(() -> {
            singleFlightCache.evict("product:" + companyId + ":" + productId);
            singleFlightCache.evictByPattern("products:batch:" + companyId + ":*");
        });
    }

    // --- Variants ---

    @Override
    public List<ProductVariantResponse> getProductVariants(long companyId, long productId) {
        assertProductBelongsToCompany(companyId, productId);
        return productVariantRepository.findAllByProductIdOrderByDisplayOrderAsc(productId)
                .stream()
                .map(this::toVariantResponse)
                .toList();
    }

    @Override
    public ProductVariantResponse getProductVariant(long companyId, long productId, long variantId) {
        assertProductBelongsToCompany(companyId, productId);
        ProductVariant variant = productVariantRepository.findByIdAndProductId(variantId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found with id: " + variantId));
        return toVariantResponse(variant);
    }

    @Override
    @Transactional
    public ProductVariantResponse createProductVariant(long companyId, long productId, long ownerId, CreateProductVariantRequest request) {
        companyRepository.findByIdAndOwnerId(companyId, ownerId)
                .orElseThrow(() -> new ForbiddenException("You do not own this company"));

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

        ProductVariantResponse result = toVariantResponse(productVariantRepository.save(variant));
        evictAfterCommit(() -> {
            singleFlightCache.evict("product:" + companyId + ":" + productId);
            singleFlightCache.evictByPattern("products:batch:" + companyId + ":*");
        });
        return result;
    }

    @Override
    @Transactional
    public ProductVariantResponse updateProductVariant(long companyId, long productId, long variantId, long ownerId, UpdateProductVariantRequest request) {
        companyRepository.findByIdAndOwnerId(companyId, ownerId)
                .orElseThrow(() -> new ForbiddenException("You do not own this company"));

        assertProductBelongsToCompany(companyId, productId);

        ProductVariant variant = productVariantRepository.findByIdAndProductId(variantId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found with id: " + variantId));

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
        if (request.getOption1() != null) variant.setOption1(request.getOption1());
        if (request.getOption2() != null) variant.setOption2(request.getOption2());
        if (request.getOption3() != null) variant.setOption3(request.getOption3());
        if (request.getDisplayOrder() != null) variant.setDisplayOrder(request.getDisplayOrder());

        ProductVariantResponse result = toVariantResponse(productVariantRepository.save(variant));
        evictAfterCommit(() -> {
            singleFlightCache.evict("product:" + companyId + ":" + productId);
            singleFlightCache.evictByPattern("products:batch:" + companyId + ":*");
        });
        return result;
    }

    @Override
    @Transactional
    public void deleteProductVariant(long companyId, long productId, long variantId, long ownerId) {
        companyRepository.findByIdAndOwnerId(companyId, ownerId)
                .orElseThrow(() -> new ForbiddenException("You do not own this company"));

        assertProductBelongsToCompany(companyId, productId);

        ProductVariant variant = productVariantRepository.findByIdAndProductId(variantId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found with id: " + variantId));

        productVariantRepository.delete(variant);
        evictAfterCommit(() -> {
            singleFlightCache.evict("product:" + companyId + ":" + productId);
            singleFlightCache.evictByPattern("products:batch:" + companyId + ":*");
        });
    }

    // --- Attributes ---

    @Override
    public List<ProductAttributeResponse> getProductAttributes(long companyId, long productId) {
        assertProductBelongsToCompany(companyId, productId);
        return productAttributeRepository.findAllByProductIdOrderByDisplayOrderAsc(productId)
                .stream()
                .map(this::toAttrResponse)
                .toList();
    }

    @Override
    @Transactional
    public List<ProductAttributeResponse> setProductAttributes(long companyId, long productId, long ownerId, SetProductAttributesRequest request) {
        companyRepository.findByIdAndOwnerId(companyId, ownerId)
                .orElseThrow(() -> new ForbiddenException("You do not own this company"));

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
            singleFlightCache.evictByPattern("products:batch:" + companyId + ":*");
        });
        return result;
    }

    // --- Marketplace catalog ---

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<MarketplaceCatalogProductResponse> searchMarketplaceCatalog(
            long marketplaceId, String q, String category, String brand,
            BigDecimal minPrice, BigDecimal maxPrice, Boolean featured, Long vendorId,
            int page, int size, String sort, String direction) {

        if (!marketplaceProfileRepository.existsByCompanyId(marketplaceId)) {
            throw new ResourceNotFoundException("Marketplace not found");
        }
        final int clampedSize = Math.min(size, 50);
        String cacheKey = String.format("marketplace:search:%d:%s:%s:%s:%s:%s:%s:%s:%d:%d:%s:%s",
                marketplaceId, q, category, brand, minPrice, maxPrice, featured,
                vendorId, page, clampedSize, sort, direction);
        return singleFlightCache.getOrLoad(cacheKey, cacheTtl, () -> {
        String sortField = (sort != null && SORTABLE_FIELDS.contains(sort)) ? sort : "createdAt";
        Sort.Direction sortDir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, clampedSize, Sort.by(sortDir, sortField));

        // --- Elasticsearch path ---
        try {
            final long fVendorId = vendorId != null ? vendorId : 0L;
            BoolQuery.Builder bq = new BoolQuery.Builder()
                    .filter(TermQuery.of(t -> t.field("marketplaceId").value(marketplaceId))._toQuery())
                    .filter(TermQuery.of(t -> t.field("marketplaceListed").value(true))._toQuery())
                    .filter(TermQuery.of(t -> t.field("status").value("ACTIVE"))._toQuery());

            if (q != null && !q.isBlank()) {
                bq.must(MultiMatchQuery.of(mm -> mm
                        .fields("name^3", "description", "brand^2", "category", "tags", "vendorName")
                        .query(q))._toQuery());
            }
            if (category != null) bq.filter(TermQuery.of(t -> t.field("category").value(category))._toQuery());
            if (brand    != null) bq.filter(TermQuery.of(t -> t.field("brand").value(brand))._toQuery());
            if (featured != null) bq.filter(TermQuery.of(t -> t.field("featured").value(featured))._toQuery());
            if (vendorId != null) bq.filter(TermQuery.of(t -> t.field("vendorId").value(fVendorId))._toQuery());
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
                    .withQuery(bq.build()._toQuery())
                    .withPageable(pageable)
                    .build();

            SearchHits<ProductDocument> hits = elasticsearchOperations.search(esQuery, ProductDocument.class);
            List<Long> ids = hits.stream().map(h -> h.getContent().getId()).toList();

            List<Product> products = productRepository.findAllByIdInAndMarketplaceId(ids, marketplaceId);
            Map<Long, Product> productMap = products.stream().collect(Collectors.toMap(Product::getId, p -> p));

            Map<Long, MarketplaceVendor> vendorMap = buildVendorMap(marketplaceId, products);

            List<MarketplaceCatalogProductResponse> content = ids.stream()
                    .filter(productMap::containsKey)
                    .map(id -> toCatalogResponse(productMap.get(id), vendorMap.get(productMap.get(id).getCompany().getId())))
                    .toList();

            return new PagedResponse<>(new PageImpl<>(content, pageable, hits.getTotalHits()));

        } catch (Exception e) {
            log.warn("[CATALOG SEARCH] Elasticsearch unavailable: {}", e.getMessage());
            boolean hasFilters = (q != null && !q.isBlank())
                    || category != null || brand != null || vendorId != null
                    || minPrice != null || maxPrice != null || featured != null;
            if (hasFilters) {
                throw new ServiceUnavaliableException("Search is temporarily unavailable. Please try again shortly.");
            }
            log.warn("[CATALOG SEARCH] Falling back to unfiltered database listing");
        }

        // --- JPA fallback (unfiltered, no active search filters) ---
        Page<Product> productPage = productRepository.findMarketplaceListedPaged(marketplaceId, pageable);
        Map<Long, MarketplaceVendor> vendorMap = buildVendorMap(marketplaceId, productPage.getContent());
        return new PagedResponse<>(productPage.map(p -> toCatalogResponse(p, vendorMap.get(p.getCompany().getId()))));
        }, new TypeReference<PagedResponse<MarketplaceCatalogProductResponse>>() {});
    }

    @Override
    @Transactional(readOnly = true)
    public MarketplaceCatalogProductResponse getMarketplaceProduct(long marketplaceId, long productId) {
        String cacheKey = "marketplace:product:" + marketplaceId + ":" + productId;
        return singleFlightCache.getOrLoad(cacheKey, cacheTtl, () -> {
            Product product = productRepository.findByIdAndMarketplaceId(productId, marketplaceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found in this marketplace"));
            Map<Long, MarketplaceVendor> vendorMap = buildVendorMap(marketplaceId, List.of(product));
            return toCatalogResponse(product, vendorMap.get(product.getCompany().getId()));
        }, MarketplaceCatalogProductResponse.class);
    }

    @Override
    @Transactional(readOnly = true)
    public VendorStorefrontResponse getVendorStorefront(long marketplaceId, long vendorId) {
        String cacheKey = "marketplace:storefront:" + marketplaceId + ":" + vendorId;
        return singleFlightCache.getOrLoad(cacheKey, cacheTtl, () -> {
            MarketplaceVendor vendor = marketplaceVendorRepository.findByIdAndMarketplaceId(vendorId, marketplaceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Vendor not found in this marketplace"));

            long vendorCompanyId = vendor.getVendorCompany().getId();
            List<Product> allVendorProducts = productRepository.findMarketplaceListed(marketplaceId).stream()
                    .filter(p -> p.getCompany().getId() == vendorCompanyId)
                    .toList();

            List<MarketplaceCatalogProductResponse> featured = allVendorProducts.stream()
                    .filter(Product::isFeatured)
                    .limit(10)
                    .map(p -> toCatalogResponse(p, vendor))
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
    public ProductResponse updateMarketplaceListing(long companyId, long productId, long ownerId,
                                                     UpdateMarketplaceListingRequest request) {
        companyRepository.findByIdAndOwnerId(companyId, ownerId)
                .orElseThrow(() -> new ForbiddenException("You do not own this company"));

        Product product = productRepository.findByIdAndCompanyId(productId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        boolean listing = Boolean.TRUE.equals(request.getListed());
        long marketplaceToCheck = listing
                ? request.getMarketplaceId()
                : (product.getMarketplaceId() != null ? product.getMarketplaceId() : request.getMarketplaceId());
        if (!marketplaceVendorRepository.existsByMarketplaceIdAndVendorCompanyId(marketplaceToCheck, companyId)) {
            throw new ForbiddenException("Your company is not an approved vendor in this marketplace");
        }

        final Long oldMarketplaceId = product.getMarketplaceId();
        product.setMarketplaceId(listing ? request.getMarketplaceId() : null);
        product.setMarketplaceListed(listing);

        Product saved = productRepository.save(product);
        eventPublisher.publishEvent(new ProductIndexEvent(saved, saved.getCompany().getId()));
        final Long newMarketplaceId = saved.getMarketplaceId();
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

    // --- Helpers ---

    private void evictAfterCommit(Runnable eviction) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { eviction.run(); }
            });
        } else {
            eviction.run();
        }
    }

    private void assertCompanyExists(long companyId) {
        if (!companyRepository.existsById(companyId)) {
            throw new ResourceNotFoundException("Company not found with id: " + companyId);
        }
    }

    private void assertProductBelongsToCompany(long companyId, long productId) {
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

    private Map<Long, MarketplaceVendor> buildVendorMap(long marketplaceId, List<Product> products) {
        Set<Long> companyIds = products.stream()
                .map(p -> p.getCompany().getId())
                .collect(Collectors.toSet());
        if (companyIds.isEmpty()) return Map.of();
        return marketplaceVendorRepository
                .findByMarketplaceIdAndVendorCompanyIdIn(marketplaceId, companyIds)
                .stream()
                .collect(Collectors.toMap(mv -> mv.getVendorCompany().getId(), mv -> mv));
    }

    private MarketplaceCatalogProductResponse toCatalogResponse(Product product, MarketplaceVendor vendor) {
        List<ProductImageResponse> images = product.getImages().stream()
                .map(this::toImageResponse)
                .toList();
        List<ProductVariantResponse> variants = product.getVariants().stream()
                .map(this::toVariantResponse)
                .toList();
        String vendorName = vendor != null ? vendor.getVendorCompany().getName() : null;
        String vendorTier = vendor != null ? vendor.getTier().name() : null;
        Long vendorId     = vendor != null ? vendor.getId() : null;
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
                product.getUpdatedAt()
        );
    }

    private ProductResponse toResponse(Product product) {
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

        return toResponseWithRating(product, images, options, variants, attributes, null, 0L);
    }

    private ProductResponse toResponseWithRating(
            Product product,
            List<ProductImageResponse> images,
            List<ProductOptionResponse> options,
            List<ProductVariantResponse> variants,
            List<ProductAttributeResponse> attributes,
            Double avgRating,
            Long reviewCount) {
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
                product.getStock(),
                product.getLowStockThreshold(),
                product.getWeight(),
                product.getWeightUnit(),
                product.getStatus().name(),
                product.isFeatured(),
                product.isPurchasable(),
                product.isListed(),
                product.getCreatedAt(),
                product.getUpdatedAt(),
                avgRating,
                reviewCount
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> compareProducts(long companyId, List<Long> ids) {
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
            List<Long> foundIds = products.stream().map(Product::getId).toList();
            Map<Long, double[]> ratingMap = buildRatingMap(foundIds);

            return products.stream().map(p -> {
                double[] stats = ratingMap.getOrDefault(p.getId(), new double[]{0.0, 0.0});
                Double avgRating = stats[1] > 0 ? stats[0] : null;
                Long reviewCount = (long) stats[1];
                List<ProductImageResponse> images = p.getImages().stream().map(this::toImageResponse).toList();
                List<ProductOptionResponse> options = p.getOptions().stream().map(this::toOptionResponse).toList();
                List<ProductVariantResponse> variants = p.getVariants().stream().map(this::toVariantResponse).toList();
                List<ProductAttributeResponse> attributes = p.getAttributes().stream().map(this::toAttrResponse).toList();
                return toResponseWithRating(p, images, options, variants, attributes, avgRating, reviewCount);
            }).toList();
        }, new TypeReference<List<ProductResponse>>() {});
    }

    private Map<Long, double[]> buildRatingMap(List<Long> productIds) {
        Map<Long, double[]> map = new java.util.HashMap<>();
        try {
            List<Object[]> rows = productReviewRepository.findAverageRatingsByProductIds(productIds);
            for (Object[] row : rows) {
                long productId = ((Number) row[0]).longValue();
                double avg = ((Number) row[1]).doubleValue();
                double count = ((Number) row[2]).doubleValue();
                map.put(productId, new double[]{avg, count});
            }
        } catch (Exception e) {
            log.warn("[COMPARE] Failed to load ratings: {}", e.getMessage());
        }
        return map;
    }
}
