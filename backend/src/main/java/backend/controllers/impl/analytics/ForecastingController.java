package backend.controllers.impl.analytics;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.responses.forecasting.ForecastSummaryResponse;
import backend.dtos.responses.forecasting.ProductForecastResponse;
import backend.dtos.responses.forecasting.ReorderSuggestionResponse;
import backend.dtos.responses.forecasting.SeasonalPrepSummaryResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.services.intf.analytics.ForecastingService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.List;

@RestController
@RequestMapping("/companies/{companyId}/forecasting")
@RequireAuth
public class ForecastingController {

    private final ForecastingService forecastingService;

    public ForecastingController(ForecastingService forecastingService) {
        this.forecastingService = forecastingService;
    }

    @GetMapping
    public ResponseEntity<ForecastSummaryResponse> getCompanyForecast(
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "56") @Min(14) @Max(365) int lookbackDays,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(forecastingService.getCompanyForecast(companyId, userId, lookbackDays, limit));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/products/{productId}")
    public ResponseEntity<ProductForecastResponse> getProductForecast(
            @PathVariable UUID companyId,
            @PathVariable UUID productId,
            @RequestParam(defaultValue = "56") @Min(14) @Max(365) int lookbackDays) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(
                    forecastingService.getProductForecast(companyId, productId, userId, lookbackDays));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/reorder-suggestions")
    public ResponseEntity<List<ReorderSuggestionResponse>> getReorderSuggestions(
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "56") @Min(14) @Max(365) int lookbackDays,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(
                    forecastingService.getReorderSuggestions(companyId, userId, lookbackDays, limit));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/seasonal-prep")
    public ResponseEntity<SeasonalPrepSummaryResponse> getSeasonalPrep(
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(forecastingService.getSeasonalPrep(companyId, userId, limit));
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
