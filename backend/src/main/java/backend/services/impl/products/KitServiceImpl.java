package backend.services.impl.products;

import backend.dtos.requests.product.CreateKitRequest;
import backend.dtos.requests.product.ImportFromCollectionRequest;
import backend.dtos.requests.product.KitSlotChoiceRequest;
import backend.dtos.requests.product.KitSlotRequest;
import backend.dtos.requests.product.UpdateKitRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.product.KitResponse;
import backend.dtos.responses.product.KitSlotChoiceResponse;
import backend.dtos.responses.product.KitSlotResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.CollectionProduct;
import backend.models.core.KitSlot;
import backend.models.core.KitSlotChoice;
import backend.models.core.Product;
import backend.models.core.ProductKit;
import backend.models.core.ProductVariant;
import backend.models.enums.CompanyCapability;
import backend.models.enums.ProductStatus;
import backend.repositories.CollectionProductRepository;
import backend.repositories.OrderRepository;
import backend.repositories.ProductKitRepository;
import backend.repositories.ProductRepository;
import backend.repositories.ProductVariantRepository;
import backend.services.impl.SingleFlightCache;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.products.KitService;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class KitServiceImpl implements KitService {

    private final ProductKitRepository kitRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final CollectionProductRepository collectionProductRepository;
    private final OrderRepository orderRepository;
    private final CompanyAccessService companyAccessService;
    private final SingleFlightCache singleFlightCache;
    private final long cacheTtl;

    public KitServiceImpl(
            ProductKitRepository kitRepository,
            ProductRepository productRepository,
            ProductVariantRepository variantRepository,
            CollectionProductRepository collectionProductRepository,
            OrderRepository orderRepository,
            CompanyAccessService companyAccessService,
            SingleFlightCache singleFlightCache,
            @Value("${app.product.cache-ttl-seconds:300}") long cacheTtl) {
        this.kitRepository = kitRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.collectionProductRepository = collectionProductRepository;
        this.orderRepository = orderRepository;
        this.companyAccessService = companyAccessService;
        this.singleFlightCache = singleFlightCache;
        this.cacheTtl = cacheTtl;
    }

    // --- Owner-authenticated CRUD ---

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<KitResponse> listKits(UUID companyId, UUID ownerId, ProductStatus status, int page, int size) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.READ_PRODUCTS);
        final int clampedSize = Math.min(size, 50);
        Pageable pageable = PageRequest.of(page, clampedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        // Owner/admin read honors the requested status (or returns every status when none is given).
        // Not served from the public list cache — drafts/inactive kits must never leak there.
        org.springframework.data.domain.Page<ProductKit> kits = (status != null)
                ? kitRepository.findAllByCompanyIdAndStatus(companyId, status, pageable)
                : kitRepository.findAllByCompanyId(companyId, pageable);
        return new PagedResponse<>(kits.map(this::toResponse));
    }

    @Override
    @Transactional
    public KitResponse createKit(UUID companyId, UUID ownerId, CreateKitRequest request) {
        var company = companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PRODUCTS);

        ProductKit kit = new ProductKit();
        kit.setCompany(company);
        kit.setName(request.getName());
        kit.setDescription(request.getDescription());
        kit.setThumbnailUrl(request.getThumbnailUrl());
        kit.setCurrency(request.getCurrency() != null ? request.getCurrency() : "USD");
        kit.setListed(request.isListed());
        rejectScheduledStatus(request.getStatus());
        kit.setStatus(request.getStatus() != null ? request.getStatus() : ProductStatus.DRAFT);
        if (request.getCompareAtPrice() != null) kit.setCompareAtPrice(request.getCompareAtPrice());

        List<KitSlot> slots = buildSlots(kit, request.getSlots(), companyId);
        kit.setSlots(slots);

        ProductKit saved = kitRepository.save(kit);
        evictAfterCommit(() -> singleFlightCache.evictByPattern("kits:list:" + companyId + ":*"));
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public KitResponse getKit(UUID companyId, UUID kitId, UUID ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.READ_PRODUCTS);
        ProductKit kit = kitRepository.findByIdAndCompanyId(kitId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Kit not found with id: " + kitId));
        return toResponse(kit);
    }

    @Override
    @Transactional
    public KitResponse updateKit(UUID companyId, UUID kitId, UUID ownerId, UpdateKitRequest request) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PRODUCTS);

        ProductKit kit = kitRepository.findByIdAndCompanyId(kitId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Kit not found with id: " + kitId));

        if (request.getName() != null)         kit.setName(request.getName());
        if (request.getDescription() != null)  kit.setDescription(request.getDescription());
        if (request.getThumbnailUrl() != null) kit.setThumbnailUrl(request.getThumbnailUrl());
        if (request.getCompareAtPrice() != null) kit.setCompareAtPrice(request.getCompareAtPrice());
        if (request.getCurrency() != null)     kit.setCurrency(request.getCurrency());
        if (request.getListed() != null)       kit.setListed(request.getListed());
        if (request.getStatus() != null) {
            rejectScheduledStatus(request.getStatus());
            kit.setStatus(request.getStatus());
        }

        if (request.getSlots() != null) {
            if (request.getSlots().isEmpty()) {
                throw new BadRequestException("Kit must have at least one slot");
            }
            kit.getSlots().clear();
            List<KitSlot> newSlots = buildSlots(kit, request.getSlots(), companyId);
            kit.getSlots().addAll(newSlots);
        }

        ProductKit saved = kitRepository.save(kit);
        evictAfterCommit(() -> {
            singleFlightCache.evict("kit:" + companyId + ":" + kitId);
            singleFlightCache.evictByPattern("kits:list:" + companyId + ":*");
        });
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteKit(UUID companyId, UUID kitId, UUID ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PRODUCTS);

        ProductKit kit = kitRepository.findByIdAndCompanyId(kitId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Kit not found with id: " + kitId));

        // Block deletion when ANY order references the kit — including delivered/cancelled history —
        // since OrderItem keeps a kit FK and a hard delete would fail the constraint or corrupt
        // historical order/reporting data. Archive rather than delete.
        if (orderRepository.existsAnyOrderWithKit(kitId)) {
            throw new backend.exceptions.http.ConflictException(
                    "Kit cannot be deleted because it is referenced by one or more orders");
        }

        kitRepository.delete(kit);
        evictAfterCommit(() -> {
            singleFlightCache.evict("kit:" + companyId + ":" + kitId);
            singleFlightCache.evictByPattern("kits:list:" + companyId + ":*");
        });
    }

    @Override
    @Transactional
    public KitResponse importChoicesFromCollection(UUID companyId, UUID kitId, UUID ownerId, ImportFromCollectionRequest request) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_PRODUCTS);

        ProductKit kit = kitRepository.findByIdAndCompanyId(kitId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Kit not found with id: " + kitId));

        KitSlot slot = kit.getSlots().stream()
                .filter(s -> s.getId().equals(request.getSlotId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Slot not found with id: " + request.getSlotId()));

        List<CollectionProduct> members = collectionProductRepository.findAllByCollectionId(request.getCollectionId());
        if (members.isEmpty()) {
            throw new BadRequestException("Collection is empty or not found");
        }

        int nextOrder = slot.getChoices().isEmpty() ? 0
                : slot.getChoices().stream().mapToInt(KitSlotChoice::getDisplayOrder).max().orElse(0) + 1;

        java.util.Set<UUID> existingProductIds = slot.getChoices().stream()
                .filter(c -> c.getVariant() == null)
                .map(c -> c.getProduct().getId())
                .collect(java.util.stream.Collectors.toSet());

        for (CollectionProduct cp : members) {
            Product product = cp.getProduct();
            if (!existingProductIds.contains(product.getId())) {
                KitSlotChoice choice = new KitSlotChoice();
                choice.setSlot(slot);
                choice.setProduct(product);
                choice.setVariant(null);
                choice.setPriceDelta(null);
                choice.setDefaultChoice(false);
                choice.setDisplayOrder(nextOrder++);
                slot.getChoices().add(choice);
                existingProductIds.add(product.getId());
            }
        }

        ProductKit saved = kitRepository.save(kit);
        evictAfterCommit(() -> {
            singleFlightCache.evict("kit:" + companyId + ":" + kitId);
            singleFlightCache.evictByPattern("kits:list:" + companyId + ":*");
        });
        return toResponse(saved);
    }

    // --- Public read ---

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<KitResponse> listKits(UUID companyId, ProductStatus status, int page, int size) {
        final int clampedSize = Math.min(size, 50);
        String cacheKey = "kits:list:" + companyId + ":" + page + ":" + clampedSize;
        return singleFlightCache.getOrLoad(cacheKey, cacheTtl, () -> {
            Pageable pageable = PageRequest.of(page, clampedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
            // Public endpoint — restrict to ACTIVE + listed kits regardless of requested status,
            // matching the product visibility model and checkout availability (ACTIVE + listed).
            return new PagedResponse<>(
                    kitRepository.findAllByCompanyIdAndStatusAndListed(companyId, ProductStatus.ACTIVE, true, pageable)
                            .map(this::toResponse));
        }, new TypeReference<PagedResponse<KitResponse>>() {});
    }

    @Override
    @Transactional(readOnly = true)
    public KitResponse getKit(UUID companyId, UUID kitId) {
        String cacheKey = "kit:" + companyId + ":" + kitId;
        return singleFlightCache.getOrLoad(cacheKey, cacheTtl, () -> {
            ProductKit kit = kitRepository.findByIdAndCompanyId(kitId, companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Kit not found with id: " + kitId));
            // Public read requires ACTIVE + listed — an unlisted kit is hidden from the storefront
            // even when ACTIVE (matches checkout, which requires both).
            if (kit.getStatus() != ProductStatus.ACTIVE || !kit.isListed()) {
                throw new ResourceNotFoundException("Kit not found with id: " + kitId);
            }
            return toResponse(kit);
        }, KitResponse.class);
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
     * Kits have no scheduled-publish date or activation worker (unlike products), so SCHEDULED would
     * strand a kit with no path to ACTIVE. Reject it outright on create/update.
     */
    private void rejectScheduledStatus(ProductStatus status) {
        if (status == ProductStatus.SCHEDULED) {
            throw new backend.exceptions.http.BadRequestException(
                    "SCHEDULED status is not supported for kits — use DRAFT or ACTIVE");
        }
    }

    private List<KitSlot> buildSlots(ProductKit kit, List<KitSlotRequest> slotRequests, UUID companyId) {
        List<KitSlot> slots = new ArrayList<>();
        for (KitSlotRequest sr : slotRequests) {
            if (sr.getMinQty() > sr.getMaxQty()) {
                throw new BadRequestException(
                        "Slot '" + sr.getName() + "': minQty (" + sr.getMinQty() + ") cannot exceed maxQty (" + sr.getMaxQty() + ")");
            }

            KitSlot slot = new KitSlot();
            slot.setKit(kit);
            slot.setName(sr.getName());
            slot.setDescription(sr.getDescription());
            slot.setRequired(sr.isRequired());
            slot.setMinQty(sr.getMinQty());
            slot.setMaxQty(sr.getMaxQty());
            slot.setDisplayOrder(sr.getDisplayOrder());

            if (sr.getChoices() != null && !sr.getChoices().isEmpty()) {
                List<KitSlotChoice> choices = buildChoices(slot, sr.getChoices(), companyId);
                slot.setChoices(choices);
            }

            slots.add(slot);
        }
        return slots;
    }

    private List<KitSlotChoice> buildChoices(KitSlot slot, List<KitSlotChoiceRequest> choiceRequests, UUID companyId) {
        long defaultCount = choiceRequests.stream().filter(KitSlotChoiceRequest::isDefaultChoice).count();
        if (defaultCount > 1) {
            throw new BadRequestException("Slot '" + slot.getName() + "' can have at most one default choice");
        }

        List<KitSlotChoice> choices = new ArrayList<>();
        for (KitSlotChoiceRequest cr : choiceRequests) {
            // Choice products are validated for company ownership only — NOT for listed/ACTIVE. This is
            // intentional: the project's model is "listed=false means hidden-but-buyable via a direct
            // link", and a public kit is exactly such a link, so a kit may legitimately offer unlisted
            // ("kit-only") choice products. Checkout still enforces ACTIVE + purchasable per selected
            // choice. If the policy ever changes to "unlisted = invisible everywhere", add a gate here
            // (and in checkout) — see audit round 12 #7.
            Product product = productRepository.findByIdAndCompanyId(cr.getProductId(), companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + cr.getProductId()));

            ProductVariant variant = null;
            if (cr.getVariantId() != null) {
                variant = variantRepository.findByIdAndProductId(cr.getVariantId(), product.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Variant not found with id: " + cr.getVariantId()));
            }

            KitSlotChoice choice = new KitSlotChoice();
            choice.setSlot(slot);
            choice.setProduct(product);
            choice.setVariant(variant);
            choice.setPriceDelta(cr.getPriceDelta());
            choice.setDefaultChoice(cr.isDefaultChoice());
            choice.setDisplayOrder(cr.getDisplayOrder());
            choices.add(choice);
        }
        return choices;
    }

    private BigDecimal choiceEffectivePrice(KitSlotChoice c) {
        BigDecimal base = c.getVariant() != null ? c.getVariant().getPrice() : c.getProduct().getPrice();
        return base.add(c.getPriceDelta() != null ? c.getPriceDelta() : BigDecimal.ZERO);
    }

    private boolean choiceInStock(KitSlotChoice c) {
        Product p = c.getProduct();
        if (p.getStatus() != ProductStatus.ACTIVE || !p.isPurchasable()) return false;
        if (c.getVariant() != null) {
            ProductVariant v = c.getVariant();
            return v.isPurchasable()
                    && (v.getStock() == null || v.getStock() > 0 || v.isBackorderEnabled() || v.isPreorderEnabled());
        }
        return p.getStock() == null || p.getStock() > 0 || p.isBackorderEnabled() || p.isPreorderEnabled();
    }

    private boolean kitPurchasable(ProductKit kit) {
        for (KitSlot slot : kit.getSlots()) {
            if (!slot.isRequired()) continue;
            boolean hasAvailable = slot.getChoices().stream().anyMatch(this::choiceInStock);
            if (!hasAvailable) return false;
        }
        return true;
    }

    private KitResponse toResponse(ProductKit kit) {
        List<KitSlotResponse> slotResponses = kit.getSlots().stream()
                .map(slot -> {
                    List<KitSlotChoiceResponse> choiceResponses = slot.getChoices().stream()
                            .map(c -> {
                                BigDecimal base = c.getVariant() != null
                                        ? c.getVariant().getPrice()
                                        : c.getProduct().getPrice();
                                BigDecimal effective = choiceEffectivePrice(c);
                                return new KitSlotChoiceResponse(
                                        c.getId(),
                                        c.getProduct().getId(),
                                        c.getProduct().getName(),
                                        c.getProduct().getThumbnailUrl(),
                                        c.getVariant() != null ? c.getVariant().getId() : null,
                                        c.getVariant() != null ? buildVariantTitle(c.getVariant()) : null,
                                        c.getVariant() != null ? c.getVariant().getSku() : null,
                                        base,
                                        c.getPriceDelta(),
                                        effective,
                                        c.isDefaultChoice(),
                                        c.getDisplayOrder(),
                                        choiceInStock(c));
                            })
                            .toList();
                    return new KitSlotResponse(
                            slot.getId(),
                            slot.getName(),
                            slot.getDescription(),
                            slot.isRequired(),
                            slot.getMinQty(),
                            slot.getMaxQty(),
                            slot.getDisplayOrder(),
                            choiceResponses);
                })
                .toList();

        BigDecimal computedMin = computedMinPrice(kit);
        BigDecimal computedMax = computedMaxPrice(kit);

        return new KitResponse(
                kit.getId(),
                kit.getCompany().getId(),
                kit.getName(),
                kit.getDescription(),
                kit.getThumbnailUrl(),
                computedMin,
                computedMax,
                kit.getCompareAtPrice(),
                kit.getCurrency(),
                kit.getStatus().name(),
                kit.isListed(),
                kitPurchasable(kit),
                slotResponses,
                kit.getCreatedAt(),
                kit.getUpdatedAt());
    }

    private BigDecimal computedMinPrice(ProductKit kit) {
        return kit.getSlots().stream()
                .filter(KitSlot::isRequired)
                .map(slot -> slot.getChoices().stream()
                        .map(this::choiceEffectivePrice)
                        .min(BigDecimal::compareTo)
                        .orElse(BigDecimal.ZERO)
                        .multiply(BigDecimal.valueOf(slot.getMinQty())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal computedMaxPrice(ProductKit kit) {
        return kit.getSlots().stream()
                .map(slot -> slot.getChoices().stream()
                        .map(this::choiceEffectivePrice)
                        .max(BigDecimal::compareTo)
                        .orElse(BigDecimal.ZERO)
                        .multiply(BigDecimal.valueOf(slot.getMaxQty())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String buildVariantTitle(ProductVariant variant) {
        String title = Stream.of(variant.getOption1(), variant.getOption2(), variant.getOption3())
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.joining(" / "));
        return title.isBlank() ? null : title;
    }
}
