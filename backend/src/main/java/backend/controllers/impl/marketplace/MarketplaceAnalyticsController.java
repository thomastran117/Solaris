package backend.controllers.impl.marketplace;

import java.util.UUID;
import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.sla.CreateSLAPolicyRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.marketplace.MarketplaceAnalyticsSummaryResponse;
import backend.dtos.responses.marketplace.TopVendorResponse;
import backend.dtos.responses.sla.VendorSLABreachResponse;
import backend.dtos.responses.sla.VendorSLAMetricResponse;
import backend.dtos.responses.sla.VendorSLAPolicyResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.services.intf.vendors.VendorAnalyticsService;
import backend.services.intf.vendors.VendorSLAService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequireAuth
public class MarketplaceAnalyticsController {

    private final VendorAnalyticsService vendorAnalyticsService;
    private final VendorSLAService vendorSLAService;

    public MarketplaceAnalyticsController(
            VendorAnalyticsService vendorAnalyticsService,
            VendorSLAService vendorSLAService) {
        this.vendorAnalyticsService = vendorAnalyticsService;
        this.vendorSLAService = vendorSLAService;
    }

    // -------------------------------------------------------------------------
    // Marketplace analytics (operator)
    // -------------------------------------------------------------------------

    @GetMapping("/marketplaces/{marketplaceId}/analytics/summary")
    public ResponseEntity<MarketplaceAnalyticsSummaryResponse> getMarketplaceSummary(
            @PathVariable UUID marketplaceId,
            @RequestParam(defaultValue = "30") int days) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(vendorAnalyticsService.getMarketplaceSummary(marketplaceId, userId, days));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/marketplaces/{marketplaceId}/analytics/top-vendors")
    public ResponseEntity<List<TopVendorResponse>> getTopVendors(
            @PathVariable UUID marketplaceId,
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "10") int limit) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(vendorAnalyticsService.getTopVendors(marketplaceId, userId, days, limit));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    // -------------------------------------------------------------------------
    // SLA policies (operator)
    // -------------------------------------------------------------------------

    @PostMapping("/marketplaces/{marketplaceId}/sla/policies")
    public ResponseEntity<VendorSLAPolicyResponse> createPolicy(
            @PathVariable UUID marketplaceId,
            @Valid @RequestBody CreateSLAPolicyRequest request) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(vendorSLAService.createPolicy(marketplaceId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/marketplaces/{marketplaceId}/sla/policies")
    public ResponseEntity<List<VendorSLAPolicyResponse>> listPolicies(@PathVariable UUID marketplaceId) {
        try {
            return ResponseEntity.ok(vendorSLAService.listPolicies(marketplaceId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/marketplaces/{marketplaceId}/sla/policies/active")
    public ResponseEntity<VendorSLAPolicyResponse> getActivePolicy(@PathVariable UUID marketplaceId) {
        try {
            return ResponseEntity.ok(vendorSLAService.getActivePolicy(marketplaceId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    // -------------------------------------------------------------------------
    // SLA vendor endpoints (vendor self-service + operator)
    // -------------------------------------------------------------------------

    @GetMapping("/marketplaces/{marketplaceId}/vendors/{vendorId}/sla/metrics")
    public ResponseEntity<PagedResponse<VendorSLAMetricResponse>> listMetrics(
            @PathVariable UUID marketplaceId,
            @PathVariable UUID vendorId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "30") @Min(1) @Max(90) int size) {
        try {
            return ResponseEntity.ok(vendorSLAService.listMetrics(marketplaceId, vendorId, resolveUserId(), page, size));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/marketplaces/{marketplaceId}/vendors/{vendorId}/sla/metrics/latest")
    public ResponseEntity<VendorSLAMetricResponse> getLatestMetric(
            @PathVariable UUID marketplaceId,
            @PathVariable UUID vendorId) {
        try {
            return ResponseEntity.ok(vendorSLAService.getLatestMetric(marketplaceId, vendorId, resolveUserId()));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/marketplaces/{marketplaceId}/vendors/{vendorId}/sla/breaches")
    public ResponseEntity<PagedResponse<VendorSLABreachResponse>> listBreaches(
            @PathVariable UUID marketplaceId,
            @PathVariable UUID vendorId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        try {
            return ResponseEntity.ok(vendorSLAService.listBreaches(marketplaceId, vendorId, resolveUserId(), page, size));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/marketplaces/{marketplaceId}/sla/breaches/{breachId}/resolve")
    public ResponseEntity<VendorSLABreachResponse> resolveBreach(
            @PathVariable UUID marketplaceId,
            @PathVariable UUID breachId) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(vendorSLAService.resolveBreach(breachId, userId, marketplaceId));
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
}
