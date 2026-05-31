package backend.controllers.impl.analytics;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.responses.operations.CancellationMetricResponse;
import backend.dtos.responses.operations.DurationMetricResponse;
import backend.dtos.responses.operations.OperationsSummaryResponse;
import backend.dtos.responses.operations.StockoutMetricResponse;
import backend.dtos.responses.operations.SupplierLatenessMetricResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.services.intf.analytics.OperationsDashboardService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/companies/{companyId}/operations")
@RequireAuth
public class OperationsController {

    private final OperationsDashboardService operationsService;

    public OperationsController(OperationsDashboardService operationsService) {
        this.operationsService = operationsService;
    }

    @GetMapping("/summary")
    public ResponseEntity<OperationsSummaryResponse> getSummary(
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "30") @Min(7) @Max(365) int lookbackDays) {
        try {
            return ResponseEntity.ok(operationsService.getSummary(companyId, resolveUserId(), lookbackDays));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/fulfillment")
    public ResponseEntity<DurationMetricResponse> getFulfillment(
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "30") @Min(7) @Max(365) int lookbackDays) {
        try {
            return ResponseEntity.ok(operationsService.getFulfillmentMetric(companyId, resolveUserId(), lookbackDays));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/refunds")
    public ResponseEntity<DurationMetricResponse> getRefunds(
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "30") @Min(7) @Max(365) int lookbackDays) {
        try {
            return ResponseEntity.ok(operationsService.getRefundMetric(companyId, resolveUserId(), lookbackDays));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/pick-delays")
    public ResponseEntity<DurationMetricResponse> getPickDelays(
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "30") @Min(7) @Max(365) int lookbackDays) {
        try {
            return ResponseEntity.ok(operationsService.getPickDelayMetric(companyId, resolveUserId(), lookbackDays));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/stockouts")
    public ResponseEntity<StockoutMetricResponse> getStockouts(
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "30") @Min(7) @Max(365) int lookbackDays) {
        try {
            return ResponseEntity.ok(operationsService.getStockoutMetric(companyId, resolveUserId(), lookbackDays));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/supplier-lateness")
    public ResponseEntity<SupplierLatenessMetricResponse> getSupplierLateness(
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "30") @Min(7) @Max(365) int lookbackDays) {
        try {
            return ResponseEntity.ok(operationsService.getSupplierLatenessMetric(companyId, resolveUserId(), lookbackDays));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/cancellations")
    public ResponseEntity<CancellationMetricResponse> getCancellations(
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "30") @Min(7) @Max(365) int lookbackDays) {
        try {
            return ResponseEntity.ok(operationsService.getCancellationMetric(companyId, resolveUserId(), lookbackDays));
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
