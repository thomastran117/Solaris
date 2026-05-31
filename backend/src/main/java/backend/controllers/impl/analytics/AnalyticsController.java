package backend.controllers.impl.analytics;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.responses.analytics.CategorySalesResponse;
import backend.dtos.responses.analytics.CompanyRevenueSummaryResponse;
import backend.dtos.responses.analytics.HotProductsResponse;
import backend.dtos.responses.analytics.ProductPerformanceResponse;
import backend.dtos.responses.analytics.SlowMoversResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.services.intf.analytics.CompanyAnalyticsService;
import backend.services.intf.inventory.DemandService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/companies/{companyId}/analytics")
public class AnalyticsController {

    private final DemandService            demandService;
    private final CompanyAnalyticsService  companyAnalyticsService;

    public AnalyticsController(DemandService demandService,
                               CompanyAnalyticsService companyAnalyticsService) {
        this.demandService           = demandService;
        this.companyAnalyticsService = companyAnalyticsService;
    }

    /**
     * Returns the top products by purchase velocity within the requested time window.
     *
     * window — "1h" (last 60 minutes) or "24h" (last 24 hours). Default: "1h".
     * limit  — number of products to return, 1–50. Default: 20.
     *
     * Each entry includes:
     *   - velocityPerHour: units sold per hour within the window
     *   - accelerationRatio: for the 1h window, how much faster the product
     *     is selling compared to its own 24h average (> 1.0 = trending up)
     */
    @GetMapping("/hot-products")
    @RequireAuth
    public ResponseEntity<HotProductsResponse> getHotProducts(
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "1h") String window,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(demandService.getHotProducts(companyId, userId, window, limit));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/revenue-summary")
    @RequireAuth
    public ResponseEntity<CompanyRevenueSummaryResponse> getRevenueSummary(
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "30") @Min(7) @Max(365) int lookbackDays) {
        try {
            return ResponseEntity.ok(
                    companyAnalyticsService.getRevenueSummary(companyId, resolveUserId(), lookbackDays));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/category-sales")
    @RequireAuth
    public ResponseEntity<CategorySalesResponse> getCategorySales(
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "30") @Min(7) @Max(365) int lookbackDays) {
        try {
            return ResponseEntity.ok(
                    companyAnalyticsService.getCategorySales(companyId, resolveUserId(), lookbackDays));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/slow-movers")
    @RequireAuth
    public ResponseEntity<SlowMoversResponse> getSlowMovers(
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "90") @Min(7) @Max(365) int days) {
        try {
            return ResponseEntity.ok(
                    companyAnalyticsService.getSlowMovers(companyId, resolveUserId(), days));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/product-performance")
    @RequireAuth
    public ResponseEntity<ProductPerformanceResponse> getProductPerformance(
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "30") @Min(7) @Max(365) int lookbackDays) {
        try {
            return ResponseEntity.ok(
                    companyAnalyticsService.getProductPerformance(companyId, resolveUserId(), lookbackDays));
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
