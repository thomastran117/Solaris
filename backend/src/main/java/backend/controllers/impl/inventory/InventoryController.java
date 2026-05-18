package backend.controllers.impl.inventory;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.inventory.AdjustStockRequest;
import backend.dtos.requests.inventory.BulkAdjustRequest;
import backend.dtos.requests.inventory.UpdateInventorySettingsRequest;
import backend.dtos.responses.general.CursorPagedResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.inventory.AdjustmentResponse;
import backend.dtos.responses.inventory.InventoryItemResponse;
import backend.dtos.responses.inventory.InventorySummaryResponse;
import backend.dtos.responses.inventory.ProductSalesMetricResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.InternalServerErrorException;
import backend.models.enums.AdjustmentReason;
import backend.models.enums.ProductStatus;
import backend.dtos.responses.inventory.LocationStockResponse;
import backend.services.intf.inventory.InventoryService;
import backend.services.intf.inventory.LocationInventoryService;
import backend.services.intf.SanitizationService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Validated
@RestController
@RequestMapping("/companies/{companyId}/inventory")
@RequireAuth
public class InventoryController {

    private final InventoryService inventoryService;
    private final LocationInventoryService locationInventoryService;
    private final SanitizationService sanitizationService;

    public InventoryController(InventoryService inventoryService,
                               LocationInventoryService locationInventoryService,
                               SanitizationService sanitizationService) {
        this.inventoryService = inventoryService;
        this.locationInventoryService = locationInventoryService;
        this.sanitizationService = sanitizationService;
    }

    @GetMapping
    public ResponseEntity<CursorPagedResponse<InventoryItemResponse>> getInventory(
            @PathVariable UUID companyId,
            @RequestParam(required = false) @Size(max = 200) String q,
            @RequestParam(required = false) @Size(max = 50) String stockStatus,
            @RequestParam(required = false) @Size(max = 100) String category,
            @RequestParam(required = false) @Size(max = 100) String brand,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) Integer minStock,
            @RequestParam(required = false) Integer maxStock,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(inventoryService.getInventory(
                    companyId, userId, stockStatus, q, category, brand, status, minStock, maxStock,
                    cursor, size));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/summary")
    public ResponseEntity<InventorySummaryResponse> getSummary(
            @PathVariable UUID companyId) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(inventoryService.getSummary(companyId, userId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/{productId}")
    public ResponseEntity<InventoryItemResponse> getInventoryItem(
            @PathVariable UUID companyId,
            @PathVariable UUID productId) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(inventoryService.getInventoryItem(companyId, productId, userId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/{productId}/history")
    public ResponseEntity<PagedResponse<AdjustmentResponse>> getAdjustmentHistory(
            @PathVariable UUID companyId,
            @PathVariable UUID productId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(inventoryService.getAdjustmentHistory(companyId, productId, userId, page, size));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{productId}/adjust")
    public ResponseEntity<InventoryItemResponse> adjustStock(
            @PathVariable UUID companyId,
            @PathVariable UUID productId,
            @Valid @RequestBody AdjustStockRequest request) {
        try {
            sanitizationService.normalize(request);
            UUID userId = resolveUserId();
            return ResponseEntity.ok(inventoryService.adjustStock(companyId, productId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/bulk-adjust")
    public ResponseEntity<List<InventoryItemResponse>> bulkAdjust(
            @PathVariable UUID companyId,
            @Valid @RequestBody BulkAdjustRequest request) {
        try {
            sanitizationService.normalize(request);
            UUID userId = resolveUserId();
            return ResponseEntity.ok(inventoryService.bulkAdjust(companyId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/analytics/top-purchased")
    public ResponseEntity<List<ProductSalesMetricResponse>> getTopPurchasedProducts(
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        try {
            validateDateRange(from, to);
            UUID userId = resolveUserId();
            return ResponseEntity.ok(inventoryService.getTopPurchasedProducts(
                    companyId, userId, limit, toStartOfDay(from), toEndOfDay(to)));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/analytics/top-revenue")
    public ResponseEntity<List<ProductSalesMetricResponse>> getTopRevenueProducts(
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        try {
            validateDateRange(from, to);
            UUID userId = resolveUserId();
            return ResponseEntity.ok(inventoryService.getTopRevenueProducts(
                    companyId, userId, limit, toStartOfDay(from), toEndOfDay(to)));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/analytics/never-sold")
    public ResponseEntity<List<ProductSalesMetricResponse>> getNeverSoldProducts(
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(inventoryService.getNeverSoldProducts(companyId, userId, limit));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PatchMapping("/{productId}/settings")
    public ResponseEntity<InventoryItemResponse> updateSettings(
            @PathVariable UUID companyId,
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateInventorySettingsRequest request) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(inventoryService.updateSettings(companyId, productId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/adjustments")
    public ResponseEntity<PagedResponse<AdjustmentResponse>> getCompanyAdjustmentHistory(
            @PathVariable UUID companyId,
            @RequestParam(required = false) AdjustmentReason reason,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        try {
            validateDateRange(from, to);
            UUID ownerId = resolveUserId();
            return ResponseEntity.ok(inventoryService.getCompanyAdjustmentHistory(
                    companyId, ownerId, reason, toStartOfDay(from), toEndOfDay(to),
                    productId, userId, page, size));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/{productId}/locations")
    public ResponseEntity<List<LocationStockResponse>> getProductLocationStocks(
            @PathVariable UUID companyId,
            @PathVariable UUID productId) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(locationInventoryService.getProductLocationStocks(companyId, productId, userId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    private UUID resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (UUID) auth.getPrincipal();
    }

    private static void validateDateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new BadRequestException("End date must be equal to or after start date");
        }
    }

    private static Instant toStartOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private static Instant toEndOfDay(LocalDate date) {
        return date == null ? null : date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusNanos(1);
    }
}
