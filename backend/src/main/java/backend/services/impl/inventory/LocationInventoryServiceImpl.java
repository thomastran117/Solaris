package backend.services.impl.inventory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import backend.dtos.requests.inventory.AdjustStockRequest;
import backend.dtos.requests.inventory.CreateLocationRequest;
import backend.dtos.requests.inventory.SetLocationStockRequest;
import backend.dtos.requests.inventory.UpdateLocationRequest;
import backend.dtos.responses.inventory.LocationResponse;
import backend.dtos.responses.inventory.LocationStockResponse;
import backend.dtos.responses.inventory.NearbyPickupLocationResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.enums.CompanyCapability;
import backend.services.intf.company.CompanyAccessService;
import backend.models.core.Company;
import backend.models.core.InventoryAdjustment;
import backend.models.core.InventoryLocation;
import backend.models.core.LocationStock;
import backend.models.core.Product;
import backend.models.core.ProductVariant;
import backend.models.enums.AdjustmentReason;
import backend.models.enums.LocationType;
import backend.repositories.InventoryAdjustmentRepository;
import backend.repositories.InventoryLocationRepository;
import backend.repositories.LocationStockRepository;
import backend.repositories.ProductRepository;
import backend.repositories.ProductVariantRepository;
import backend.repositories.UserRepository;
import backend.services.intf.CacheService;
import backend.services.intf.inventory.LocationInventoryService;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;

@Service
public class LocationInventoryServiceImpl implements LocationInventoryService {

    private static final Logger log = LoggerFactory.getLogger(LocationInventoryServiceImpl.class);

    private static final String LOC_STOCK_LOCK_PREFIX = "lock:locstock:";
    private static final long LOCK_TTL_SECONDS = 10;
    private static final int LOCK_RETRY_ATTEMPTS = 5;
    private static final long LOCK_RETRY_DELAY_MS = 100;

    private final InventoryLocationRepository locationRepository;
    private final LocationStockRepository locationStockRepository;
    private final CompanyAccessService companyAccessService;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryAdjustmentRepository adjustmentRepository;
    private final UserRepository userRepository;
    private final CacheService cacheService;
    private final StockAlertService stockAlertService;

    public LocationInventoryServiceImpl(
            InventoryLocationRepository locationRepository,
            LocationStockRepository locationStockRepository,
            CompanyAccessService companyAccessService,
            ProductRepository productRepository,
            ProductVariantRepository variantRepository,
            InventoryAdjustmentRepository adjustmentRepository,
            UserRepository userRepository,
            CacheService cacheService,
            StockAlertService stockAlertService) {
        this.locationRepository = locationRepository;
        this.locationStockRepository = locationStockRepository;
        this.companyAccessService = companyAccessService;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.userRepository = userRepository;
        this.cacheService = cacheService;
        this.stockAlertService = stockAlertService;
    }

    // --- Location CRUD ---

    @Override
    public List<LocationResponse> getLocations(UUID companyId, UUID ownerId) {
        assertCompanyOwnership(companyId, ownerId);
        return locationRepository.findAllByCompanyIdOrderByDisplayOrderAscNameAsc(companyId)
                .stream()
                .map(this::toLocationResponse)
                .toList();
    }

    @Override
    public LocationResponse getLocation(UUID companyId, UUID locationId, UUID ownerId) {
        assertCompanyOwnership(companyId, ownerId);
        InventoryLocation location = locationRepository.findByIdAndCompanyId(locationId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found with id: " + locationId));
        return toLocationResponse(location);
    }

    @Override
    @Transactional
    public LocationResponse createLocation(UUID companyId, UUID ownerId, CreateLocationRequest request) {
        Company company = companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_INVENTORY);

        if (locationRepository.existsByCodeAndCompanyId(request.getCode(), companyId)) {
            throw new ConflictException("A location with code '" + request.getCode() + "' already exists in this company");
        }

        LocationType type = request.getType() != null ? request.getType() : LocationType.WAREHOUSE;
        validatePickupReadyHours(type, request.getPickupReadyHours());

        InventoryLocation location = new InventoryLocation();
        location.setCompany(company);
        location.setName(request.getName());
        location.setCode(request.getCode().toUpperCase());
        location.setAddress(request.getAddress());
        location.setCity(request.getCity());
        location.setCountry(request.getCountry());
        location.setDisplayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0);
        location.setLatitude(request.getLatitude());
        location.setLongitude(request.getLongitude());
        location.setFulfillmentCost(request.getFulfillmentCost());
        location.setType(type);
        if (request.getHandlingDays() != null) location.setHandlingDays(request.getHandlingDays());
        location.setPickupReadyHours(request.getPickupReadyHours());

        return toLocationResponse(locationRepository.save(location));
    }

    @Override
    @Transactional
    public LocationResponse updateLocation(UUID companyId, UUID locationId, UUID ownerId, UpdateLocationRequest request) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_INVENTORY);

        InventoryLocation location = locationRepository.findByIdAndCompanyId(locationId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found with id: " + locationId));

        if (request.getCode() != null && !request.getCode().equalsIgnoreCase(location.getCode())) {
            if (locationRepository.existsByCodeAndCompanyIdAndIdNot(request.getCode(), companyId, locationId)) {
                throw new ConflictException("A location with code '" + request.getCode() + "' already exists in this company");
            }
            location.setCode(request.getCode().toUpperCase());
        }

        if (request.getName() != null) location.setName(request.getName());
        if (request.getAddress() != null) location.setAddress(request.getAddress());
        if (request.getCity() != null) location.setCity(request.getCity());
        if (request.getCountry() != null) location.setCountry(request.getCountry());
        if (request.getActive() != null) location.setActive(request.getActive());
        if (request.getDisplayOrder() != null) location.setDisplayOrder(request.getDisplayOrder());
        if (request.getLatitude() != null) location.setLatitude(request.getLatitude());
        if (request.getLongitude() != null) location.setLongitude(request.getLongitude());
        if (request.getFulfillmentCost() != null) location.setFulfillmentCost(request.getFulfillmentCost());
        if (request.getType() != null) location.setType(request.getType());
        if (request.getHandlingDays() != null) location.setHandlingDays(request.getHandlingDays());
        if (request.getPickupReadyHours() != null) location.setPickupReadyHours(request.getPickupReadyHours());

        validatePickupReadyHours(location.getType(), location.getPickupReadyHours());

        return toLocationResponse(locationRepository.save(location));
    }

    private void validatePickupReadyHours(LocationType type, Integer pickupReadyHours) {
        if (type == LocationType.WAREHOUSE && pickupReadyHours != null) {
            throw new BadRequestException("pickupReadyHours must be null for WAREHOUSE locations");
        }
    }

    @Override
    @Transactional
    public void deleteLocation(UUID companyId, UUID locationId, UUID ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_INVENTORY);

        InventoryLocation location = locationRepository.findByIdAndCompanyId(locationId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found with id: " + locationId));

        if (locationStockRepository.existsByLocationIdAndStockGreaterThan(locationId, 0)) {
            throw new ConflictException("Cannot delete location with remaining stock — zero out all stock first");
        }

        locationRepository.delete(location);
    }

    // --- Stock queries ---

    @Override
    public List<LocationStockResponse> getLocationStock(UUID companyId, UUID locationId, UUID ownerId) {
        assertCompanyOwnership(companyId, ownerId);
        locationRepository.findByIdAndCompanyId(locationId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found with id: " + locationId));

        return locationStockRepository.findAllByLocationId(locationId)
                .stream()
                .map(this::toLocationStockResponse)
                .toList();
    }

    @Override
    public List<LocationStockResponse> getProductLocationStocks(UUID companyId, UUID productId, UUID ownerId) {
        assertCompanyOwnership(companyId, ownerId);
        productRepository.findByIdAndCompanyId(productId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        return locationStockRepository.findAllByProductIdAndCompanyId(productId, companyId)
                .stream()
                .map(this::toLocationStockResponse)
                .toList();
    }

    // --- Stock management ---

    @Override
    @Transactional
    public LocationStockResponse setLocationStock(UUID companyId, UUID locationId, UUID productId,
                                                   UUID ownerId, SetLocationStockRequest request,
                                                   UUID variantId) {
        assertCompanyOwnership(companyId, ownerId);

        InventoryLocation location = locationRepository.findByIdAndCompanyId(locationId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found with id: " + locationId));

        Product product = productRepository.findByIdAndCompanyId(productId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        UUID variantRef = null;
        ProductVariant variant = null;
        if (variantId != null) {
            variant = variantRepository.findByIdAndProductId(variantId, productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Variant not found with id: " + variantId));
            variantRef = variantId;
        }

        String lockKey = LOC_STOCK_LOCK_PREFIX + locationId + ":" + variantRef;
        String lockToken = UUID.randomUUID().toString();
        boolean lockAcquired = false;

        try {
            for (int attempt = 0; attempt < LOCK_RETRY_ATTEMPTS; attempt++) {
                if (cacheService.tryLock(lockKey, lockToken, LOCK_TTL_SECONDS)) {
                    lockAcquired = true;
                    break;
                }
                try {
                    Thread.sleep(LOCK_RETRY_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ConflictException("Stock update interrupted, please try again");
                }
            }

            if (!lockAcquired) {
                throw new ConflictException("Location stock is currently being updated, please try again shortly");
            }

            LocationStock locationStock = locationStockRepository
                    .findByLocationIdAndProductIdAndVariantRef(locationId, productId, variantRef)
                    .orElse(null);

            int newStock = request.getStock();
            int previousStock = locationStock != null ? locationStock.getStock() : 0;
            int delta = newStock - previousStock;

            if (locationStock == null) {
                locationStock = new LocationStock();
                locationStock.setLocation(location);
                locationStock.setProduct(product);
                locationStock.setVariant(variant);
                locationStock.setVariantRef(variantRef);
                locationStock.setStock(newStock);
                locationStock.setLowStockThreshold(request.getLowStockThreshold());
                locationStock = locationStockRepository.save(locationStock);
            } else {
                locationStockRepository.setStock(locationStock.getId(), newStock, request.getLowStockThreshold());
                locationStock.setStock(newStock);
                locationStock.setLowStockThreshold(request.getLowStockThreshold());
            }

            if (delta != 0) {
                InventoryAdjustment adjustment = new InventoryAdjustment();
                adjustment.setProduct(productRepository.getReferenceById(productId));
                if (variant != null) adjustment.setVariant(variantRepository.getReferenceById(variantId));
                adjustment.setAdjustedBy(userRepository.getReferenceById(ownerId));
                adjustment.setDelta(delta);
                adjustment.setPreviousStock(previousStock);
                adjustment.setNewStock(newStock);
                adjustment.setReason(AdjustmentReason.MANUAL_ADJUSTMENT);
                adjustment.setNote("Location stock set for location: " + location.getName());
                adjustmentRepository.save(adjustment);

                if (delta < 0) {
                    stockAlertService.checkAndAlertLocation(
                            locationStock.getId(), location.getName(),
                            productId, product.getName(),
                            variantId, newStock, locationStock.getLowStockThreshold());
                }
            }

            return toLocationStockResponse(locationStock);

        } finally {
            if (lockAcquired) {
                try {
                    cacheService.unlock(lockKey, lockToken);
                } catch (Exception e) {
                    log.error("Failed to release location stock lock {}: {}", lockKey, e.getMessage());
                }
            }
        }
    }

    @Override
    @Transactional
    public LocationStockResponse adjustLocationStock(UUID companyId, UUID locationId, UUID productId,
                                                      UUID ownerId, AdjustStockRequest request,
                                                      UUID variantId) {
        assertCompanyOwnership(companyId, ownerId);

        InventoryLocation location = locationRepository.findByIdAndCompanyId(locationId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Location not found with id: " + locationId));

        Product product = productRepository.findByIdAndCompanyId(productId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));

        UUID variantRef = null;
        ProductVariant variant = null;
        if (variantId != null) {
            variant = variantRepository.findByIdAndProductId(variantId, productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Variant not found with id: " + variantId));
            variantRef = variantId;
        }

        LocationStock locationStock = locationStockRepository
                .findByLocationIdAndProductIdAndVariantRef(locationId, productId, variantRef)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No stock record found at this location for the specified product/variant"));

        String lockKey = LOC_STOCK_LOCK_PREFIX + locationId + ":" + variantRef;
        String lockToken = UUID.randomUUID().toString();
        boolean lockAcquired = false;

        try {
            for (int attempt = 0; attempt < LOCK_RETRY_ATTEMPTS; attempt++) {
                if (cacheService.tryLock(lockKey, lockToken, LOCK_TTL_SECONDS)) {
                    lockAcquired = true;
                    break;
                }
                try {
                    Thread.sleep(LOCK_RETRY_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ConflictException("Stock adjustment interrupted, please try again");
                }
            }

            if (!lockAcquired) {
                throw new ConflictException("Location stock is currently being updated, please try again shortly");
            }

            int previousStock = locationStock.getStock();
            int delta = request.getDelta();

            int rows = locationStockRepository.adjustStock(locationStock.getId(), delta);
            if (rows == 0) {
                throw new BadRequestException(
                        "Adjustment would result in negative location stock. Current: " + previousStock + ", delta: " + delta);
            }

            InventoryAdjustment adjustment = new InventoryAdjustment();
            adjustment.setProduct(productRepository.getReferenceById(productId));
            if (variant != null) adjustment.setVariant(variantRepository.getReferenceById(variantId));
            adjustment.setAdjustedBy(userRepository.getReferenceById(ownerId));
            adjustment.setDelta(delta);
            adjustment.setPreviousStock(previousStock);
            adjustment.setNewStock(previousStock + delta);
            adjustment.setReason(request.getReason());
            adjustment.setNote(request.getNote());
            adjustmentRepository.save(adjustment);

            if (delta < 0) {
                stockAlertService.checkAndAlertLocation(
                        locationStock.getId(), location.getName(),
                        productId, product.getName(),
                        variantId, previousStock + delta, locationStock.getLowStockThreshold());
            }

            locationStock.setStock(previousStock + delta);
            return toLocationStockResponse(locationStock);

        } finally {
            if (lockAcquired) {
                try {
                    cacheService.unlock(lockKey, lockToken);
                } catch (Exception e) {
                    log.error("Failed to release location stock lock {}: {}", lockKey, e.getMessage());
                }
            }
        }
    }

    // --- Nearby pickup ---

    @Override
    @Transactional(readOnly = true)
    public List<NearbyPickupLocationResponse> getNearbyPickupLocations(UUID companyId, double lat, double lng, int limit) {
        return locationRepository.findNearbyPickupLocations(companyId, lat, lng, limit)
                .stream()
                .map(row -> new NearbyPickupLocationResponse(
                        bytesToUuid((byte[]) row[0]),
                        (String) row[1],
                        (String) row[2],
                        (String) row[3],
                        (String) row[4],
                        (String) row[5],
                        ((Number) row[8]).doubleValue(),
                        row[6] != null ? ((Number) row[6]).intValue() : null,
                        LocationType.valueOf((String) row[7])
                ))
                .toList();
    }

    // --- Helpers ---

    private void assertCompanyOwnership(UUID companyId, UUID ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_INVENTORY);
    }

    private static UUID bytesToUuid(byte[] bytes) {
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        return new UUID(bb.getLong(), bb.getLong());
    }

    private LocationResponse toLocationResponse(InventoryLocation loc) {
        return new LocationResponse(
                loc.getId(),
                loc.getCompany().getId(),
                loc.getName(),
                loc.getCode(),
                loc.getAddress(),
                loc.getCity(),
                loc.getCountry(),
                loc.isActive(),
                loc.getDisplayOrder(),
                loc.getLatitude(),
                loc.getLongitude(),
                loc.getFulfillmentCost(),
                loc.getType(),
                loc.getHandlingDays(),
                loc.getPickupReadyHours(),
                loc.getCreatedAt(),
                loc.getUpdatedAt()
        );
    }

    private LocationStockResponse toLocationStockResponse(LocationStock ls) {
        int stock = ls.getStock();
        Integer threshold = ls.getLowStockThreshold();

        String stockStatus;
        if (stock == 0) {
            stockStatus = "OUT_OF_STOCK";
        } else if (threshold != null && stock <= threshold) {
            stockStatus = "LOW_STOCK";
        } else {
            stockStatus = "IN_STOCK";
        }

        UUID variantId = ls.isProductLevel() ? null : ls.getVariant().getId();

        return new LocationStockResponse(
                ls.getId(),
                ls.getLocation().getId(),
                ls.getLocation().getName(),
                ls.getProduct().getId(),
                variantId,
                stock,
                threshold,
                stockStatus,
                ls.getUpdatedAt()
        );
    }
}
