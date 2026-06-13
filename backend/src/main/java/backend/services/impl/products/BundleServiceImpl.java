package backend.services.impl.products;

import java.util.UUID;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import backend.services.impl.SingleFlightCache;

import backend.dtos.requests.product.BatchDeleteBundlesRequest;
import backend.dtos.requests.product.BatchUpdateBundlesRequest;
import backend.dtos.requests.product.BundleItemRequest;
import backend.dtos.requests.product.CreateBundleRequest;
import backend.dtos.requests.product.UpdateBundleRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.product.BundleItemResponse;
import backend.dtos.responses.product.BundleResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.BundleItem;
import backend.models.core.Product;
import backend.models.core.ProductBundle;
import backend.models.core.ProductVariant;
import backend.models.enums.ProductStatus;
import backend.repositories.BundleRepository;
import backend.repositories.OrderRepository;
import backend.repositories.ProductRepository;
import backend.repositories.ProductVariantRepository;
import backend.events.BundleIndexEvent;
import backend.events.BundleRemoveEvent;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.products.BundleService;
import backend.models.enums.CompanyCapability;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BundleServiceImpl implements BundleService {

    private final BundleRepository bundleRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final OrderRepository orderRepository;
    private final CompanyAccessService companyAccessService;
    private final ApplicationEventPublisher eventPublisher;
    private final SingleFlightCache singleFlightCache;
    private final long cacheTtl;

    public BundleServiceImpl(
            BundleRepository bundleRepository,
            ProductRepository productRepository,
            ProductVariantRepository variantRepository,
            OrderRepository orderRepository,
            CompanyAccessService companyAccessService,
            ApplicationEventPublisher eventPublisher,
            SingleFlightCache singleFlightCache,
            @Value("${app.product.cache-ttl-seconds:300}") long cacheTtl) {
        this.bundleRepository = bundleRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.orderRepository = orderRepository;
        this.companyAccessService = companyAccessService;
        this.eventPublisher = eventPublisher;
        this.singleFlightCache = singleFlightCache;
        this.cacheTtl = cacheTtl;
    }

    // --- Owner-authenticated CRUD ---

    @Override
    public PagedResponse<BundleResponse> listBundles(UUID companyId, UUID ownerId, ProductStatus status, int page, int size) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.READ_PRODUCTS);
        return listBundles(companyId, status, page, size);
    }

    @Override
    @Transactional
    public BundleResponse createBundle(UUID companyId, UUID ownerId, CreateBundleRequest request) {
        var company = companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PRODUCTS);

        List<BundleItemRequest> itemRequests = request.getItems();
        validateNoDuplicates(itemRequests);

        ProductBundle bundle = new ProductBundle();
        bundle.setCompany(company);
        bundle.setName(request.getName());
        bundle.setDescription(request.getDescription());
        bundle.setThumbnailUrl(request.getThumbnailUrl());
        bundle.setCurrency(request.getCurrency() != null ? request.getCurrency() : "USD");
        bundle.setListed(request.isListed());
        bundle.setPreorderEnabled(request.isPreorderEnabled());
        if (request.getPreorderExpectedDate() != null) bundle.setPreorderExpectedDate(request.getPreorderExpectedDate());

        List<BundleItem> items = buildItems(bundle, itemRequests, companyId);
        bundle.setItems(items);

        BigDecimal componentTotal = computePrice(items);
        BigDecimal price = request.getPrice() != null ? request.getPrice() : componentTotal;
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new backend.exceptions.http.BadRequestException("Bundle price cannot be negative");
        }
        if (price.compareTo(componentTotal) > 0) {
            throw new backend.exceptions.http.BadRequestException(
                    "Bundle price cannot exceed the sum of component prices (" + componentTotal + ")");
        }
        bundle.setPrice(price);

        if (request.getCompareAtPrice() != null) bundle.setCompareAtPrice(request.getCompareAtPrice());

        ProductBundle saved = bundleRepository.save(bundle);
        eventPublisher.publishEvent(new BundleIndexEvent(saved));
        evictAfterCommit(() -> singleFlightCache.evictByPattern("bundles:list:" + companyId + ":*"));
        return toResponse(saved);
    }

    @Override
    public BundleResponse getBundle(UUID companyId, UUID bundleId, UUID ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.READ_PRODUCTS);
        return getBundle(companyId, bundleId);
    }

    @Override
    @Transactional
    public BundleResponse updateBundle(UUID companyId, UUID bundleId, UUID ownerId, UpdateBundleRequest request) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PRODUCTS);

        ProductBundle bundle = bundleRepository.findByIdAndCompanyId(bundleId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Bundle not found with id: " + bundleId));

        if (request.getName() != null) bundle.setName(request.getName());
        if (request.getDescription() != null) bundle.setDescription(request.getDescription());
        if (request.getThumbnailUrl() != null) bundle.setThumbnailUrl(request.getThumbnailUrl());
        if (request.getCompareAtPrice() != null) bundle.setCompareAtPrice(request.getCompareAtPrice());
        if (request.getStatus() != null) {
            rejectScheduledStatus(request.getStatus());
            bundle.setStatus(request.getStatus());
        }
        if (request.getListed() != null) bundle.setListed(request.getListed());
        if (request.getPreorderEnabled() != null) bundle.setPreorderEnabled(request.getPreorderEnabled());
        if (request.getPreorderExpectedDate() != null) bundle.setPreorderExpectedDate(request.getPreorderExpectedDate());

        if (request.getItems() != null) {
            if (request.getItems().isEmpty()) {
                throw new BadRequestException("Bundle must contain at least one item");
            }
            validateNoDuplicates(request.getItems());
            bundle.getItems().clear();
            List<BundleItem> newItems = buildItems(bundle, request.getItems(), companyId);
            bundle.getItems().addAll(newItems);

            // Recompute price from new items if no explicit price is provided
            BigDecimal componentTotal = computePrice(newItems);
            BigDecimal price = request.getPrice() != null ? request.getPrice() : componentTotal;
            if (price.compareTo(BigDecimal.ZERO) < 0 || price.compareTo(componentTotal) > 0) {
                throw new backend.exceptions.http.BadRequestException(
                        "Bundle price must be between 0 and the component total (" + componentTotal + ")");
            }
            bundle.setPrice(price);
        } else if (request.getPrice() != null) {
            BigDecimal currentComponentTotal = computePrice(bundle.getItems());
            if (request.getPrice().compareTo(BigDecimal.ZERO) < 0
                    || request.getPrice().compareTo(currentComponentTotal) > 0) {
                throw new backend.exceptions.http.BadRequestException(
                        "Bundle price must be between 0 and the component total (" + currentComponentTotal + ")");
            }
            bundle.setPrice(request.getPrice());
        } else {
            // Neither items nor price were changed, but component prices may have drifted
            // since the bundle was created (e.g., a constituent product was discounted).
            // Re-validate the stored price to ensure it still satisfies bundle invariants.
            BigDecimal currentComponentTotal = computePrice(bundle.getItems());
            if (bundle.getPrice().compareTo(currentComponentTotal) > 0) {
                // Silently cap to the new component total so the bundle is always a
                // valid deal, rather than blocking the update entirely.
                bundle.setPrice(currentComponentTotal);
            }
        }

        ProductBundle saved = bundleRepository.save(bundle);
        eventPublisher.publishEvent(new BundleIndexEvent(saved));
        evictAfterCommit(() -> {
            singleFlightCache.evict("bundle:" + companyId + ":" + bundleId);
            singleFlightCache.evictByPattern("bundles:list:" + companyId + ":*");
        });
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteBundle(UUID companyId, UUID bundleId, UUID ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PRODUCTS);

        ProductBundle bundle = bundleRepository.findByIdAndCompanyId(bundleId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Bundle not found with id: " + bundleId));

        // Block deletion when ANY order references the bundle — including delivered/cancelled
        // history — since OrderItem keeps a bundle FK and a hard delete would fail the constraint
        // or corrupt historical order/reporting data. Archive rather than delete.
        if (orderRepository.existsAnyOrderWithBundle(bundleId)) {
            throw new backend.exceptions.http.ConflictException(
                    "Bundle cannot be deleted because it is referenced by one or more orders");
        }

        bundleRepository.delete(bundle);
        eventPublisher.publishEvent(new BundleRemoveEvent(bundleId));
        evictAfterCommit(() -> {
            singleFlightCache.evict("bundle:" + companyId + ":" + bundleId);
            singleFlightCache.evictByPattern("bundles:list:" + companyId + ":*");
        });
    }

    @Override
    @Transactional
    public List<BundleResponse> batchUpdateBundles(UUID companyId, UUID ownerId, BatchUpdateBundlesRequest request) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PRODUCTS);

        rejectScheduledStatus(request.getStatus());

        List<ProductBundle> bundles = bundleRepository.findAllByIdInAndCompanyId(request.getIds(), companyId);
        if (bundles.size() != request.getIds().size()) {
            throw new ResourceNotFoundException("One or more bundles were not found in this company");
        }

        List<BundleResponse> results = new ArrayList<>();
        for (ProductBundle bundle : bundles) {
            if (request.getStatus() != null) bundle.setStatus(request.getStatus());
            if (request.getListed() != null) bundle.setListed(request.getListed());

            ProductBundle saved = bundleRepository.save(bundle);
            eventPublisher.publishEvent(new BundleIndexEvent(saved));
            results.add(toResponse(saved));
        }

        final List<UUID> updatedIds = bundles.stream().map(ProductBundle::getId).toList();
        evictAfterCommit(() -> {
            for (UUID id : updatedIds) singleFlightCache.evict("bundle:" + companyId + ":" + id);
            singleFlightCache.evictByPattern("bundles:list:" + companyId + ":*");
        });
        return results;
    }

    @Override
    @Transactional
    public void batchDeleteBundles(UUID companyId, UUID ownerId, BatchDeleteBundlesRequest request) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PRODUCTS);

        List<ProductBundle> bundles = bundleRepository.findAllByIdInAndCompanyId(request.getIds(), companyId);
        if (bundles.size() != request.getIds().size()) {
            throw new ResourceNotFoundException("One or more bundles were not found in this company");
        }

        bundleRepository.deleteAll(bundles);
        for (ProductBundle b : bundles) {
            eventPublisher.publishEvent(new BundleRemoveEvent(b.getId()));
        }

        final List<UUID> deletedIds = bundles.stream().map(ProductBundle::getId).toList();
        evictAfterCommit(() -> {
            for (UUID id : deletedIds) singleFlightCache.evict("bundle:" + companyId + ":" + id);
            singleFlightCache.evictByPattern("bundles:list:" + companyId + ":*");
        });
    }

    // --- Public read ---

    @Override
    public List<BundleResponse> compareBundles(UUID companyId, List<UUID> ids) {
        if (ids == null || ids.size() < 2 || ids.size() > 4) {
            throw new BadRequestException("Comparison requires between 2 and 4 bundle IDs");
        }
        String sortedIds = ids.stream().sorted().map(String::valueOf).collect(Collectors.joining(":"));
        String cacheKey = "bundles:compare:" + companyId + ":" + sortedIds;
        return singleFlightCache.getOrLoad(cacheKey, cacheTtl, () -> {
            List<ProductBundle> bundles = bundleRepository.findAllByIdInAndCompanyId(ids, companyId);
            if (bundles.isEmpty()) {
                throw new ResourceNotFoundException("No bundles found for the given IDs in this company");
            }
            return bundles.stream().map(this::toResponse).toList();
        }, new TypeReference<List<BundleResponse>>() {});
    }

    @Override
    public PagedResponse<BundleResponse> listBundles(UUID companyId, ProductStatus status, int page, int size) {
        final int clampedSize = Math.min(size, 50);
        String cacheKey = "bundles:list:" + companyId + ":" + page + ":" + clampedSize;
        return singleFlightCache.getOrLoad(cacheKey, cacheTtl, () -> {
            Pageable pageable = PageRequest.of(page, clampedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
            // Public endpoint — always restrict to ACTIVE bundles regardless of requested status.
            return new PagedResponse<>(
                    bundleRepository.findAllByCompanyIdAndStatus(companyId, ProductStatus.ACTIVE, pageable)
                            .map(this::toResponse));
        }, new TypeReference<PagedResponse<BundleResponse>>() {});
    }

    @Override
    public BundleResponse getBundle(UUID companyId, UUID bundleId) {
        String cacheKey = "bundle:" + companyId + ":" + bundleId;
        return singleFlightCache.getOrLoad(cacheKey, cacheTtl, () -> {
            ProductBundle bundle = bundleRepository.findByIdAndCompanyId(bundleId, companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Bundle not found with id: " + bundleId));
            if (bundle.getStatus() != ProductStatus.ACTIVE) {
                throw new ResourceNotFoundException("Bundle not found with id: " + bundleId);
            }
            return toResponse(bundle);
        }, BundleResponse.class);
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

    /**
     * Bundles have no scheduled-publish date or activation worker (unlike products), so SCHEDULED
     * would strand a bundle with no path to ACTIVE. Reject it on update (individual and bulk).
     */
    private void rejectScheduledStatus(ProductStatus status) {
        if (status == ProductStatus.SCHEDULED) {
            throw new BadRequestException(
                    "SCHEDULED status is not supported for bundles — use DRAFT or ACTIVE");
        }
    }

    private void validateNoDuplicates(List<BundleItemRequest> itemRequests) {
        Set<String> seen = new HashSet<>();
        for (BundleItemRequest ir : itemRequests) {
            String key = ir.getProductId() + ":" + ir.getVariantId();
            if (!seen.add(key)) {
                throw new BadRequestException("Duplicate product/variant combination in bundle items");
            }
        }
    }

    private List<BundleItem> buildItems(ProductBundle bundle, List<BundleItemRequest> itemRequests, UUID companyId) {
        if (itemRequests.size() > 10) {
            throw new BadRequestException("Bundle cannot have more than 10 products");
        }

        List<BundleItem> items = new ArrayList<>();
        for (BundleItemRequest ir : itemRequests) {
            Product product = productRepository.findByIdAndCompanyId(ir.getProductId(), companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + ir.getProductId()));

            ProductVariant variant = null;
            if (ir.getVariantId() != null) {
                variant = variantRepository.findByIdAndProductId(ir.getVariantId(), product.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Variant not found with id: " + ir.getVariantId()));
            }

            BundleItem item = new BundleItem();
            item.setBundle(bundle);
            item.setProduct(product);
            item.setVariant(variant);
            item.setQuantity(ir.getQuantity());
            item.setDisplayOrder(ir.getDisplayOrder());
            items.add(item);
        }
        return items;
    }

    private BigDecimal computePrice(List<BundleItem> items) {
        return items.stream()
                .map(i -> {
                    BigDecimal unitPrice = i.getVariant() != null
                            ? i.getVariant().getPrice()
                            : i.getProduct().getPrice();
                    return unitPrice.multiply(BigDecimal.valueOf(i.getQuantity()));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BundleResponse toResponse(ProductBundle b) {
        List<BundleItemResponse> items = b.getItems().stream()
                .map(i -> new BundleItemResponse(
                        i.getId(),
                        i.getProduct().getId(),
                        i.getProduct().getName(),
                        i.getVariant() != null ? i.getVariant().getId() : null,
                        i.getVariant() != null ? i.getVariant().getSku() : null,
                        i.getVariant() != null ? buildVariantTitle(i.getVariant()) : null,
                        i.getQuantity(),
                        i.getDisplayOrder()))
                .toList();

        return new BundleResponse(
                b.getId(),
                b.getCompany().getId(),
                b.getName(),
                b.getDescription(),
                b.getThumbnailUrl(),
                b.getPrice(),
                b.getCompareAtPrice(),
                b.getCurrency(),
                b.getStatus().name(),
                b.isListed(),
                b.isPreorderEnabled(),
                b.getPreorderExpectedDate(),
                items,
                b.getCreatedAt(),
                b.getUpdatedAt());
    }

    private static String buildVariantTitle(ProductVariant variant) {
        String title = java.util.stream.Stream.of(variant.getOption1(), variant.getOption2(), variant.getOption3())
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.joining(" / "));
        return title.isBlank() ? null : title;
    }
}
