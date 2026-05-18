package backend.controllers.impl.promotions;

import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.pricing.CreatePromotionRuleRequest;
import backend.dtos.requests.pricing.UpdatePromotionRuleRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.pricing.PromotionRuleAnalyticsResponse;
import backend.dtos.responses.pricing.PromotionRuleResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.services.intf.pricing.PricingReportService;
import backend.services.intf.promotions.PromotionRuleService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.Instant;

@RestController
@RequestMapping("/companies/{companyId}/promotion-rules")
public class PromotionRuleController {

    private final PromotionRuleService promotionRuleService;
    private final PricingReportService pricingReportService;

    public PromotionRuleController(
            PromotionRuleService promotionRuleService,
            PricingReportService pricingReportService) {
        this.promotionRuleService = promotionRuleService;
        this.pricingReportService = pricingReportService;
    }

    @GetMapping
    @RequireAuth
    public ResponseEntity<PagedResponse<PromotionRuleResponse>> listRules(
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        try {
            return ResponseEntity.ok(promotionRuleService.listRules(companyId, resolveUserId(), page, size));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/{ruleId}")
    @RequireAuth
    public ResponseEntity<PromotionRuleResponse> getRule(
            @PathVariable UUID companyId,
            @PathVariable UUID ruleId) {
        try {
            return ResponseEntity.ok(promotionRuleService.getRule(companyId, ruleId, resolveUserId()));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping
    @RequireAuth
    public ResponseEntity<PromotionRuleResponse> createRule(
            @PathVariable UUID companyId,
            @Valid @RequestBody CreatePromotionRuleRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(promotionRuleService.createRule(companyId, resolveUserId(), request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PatchMapping("/{ruleId}")
    @RequireAuth
    public ResponseEntity<PromotionRuleResponse> updateRule(
            @PathVariable UUID companyId,
            @PathVariable UUID ruleId,
            @Valid @RequestBody UpdatePromotionRuleRequest request) {
        try {
            return ResponseEntity.ok(promotionRuleService.updateRule(companyId, ruleId, resolveUserId(), request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @DeleteMapping("/{ruleId}")
    @RequireAuth
    public ResponseEntity<Void> deleteRule(
            @PathVariable UUID companyId,
            @PathVariable UUID ruleId) {
        try {
            promotionRuleService.deleteRule(companyId, ruleId, resolveUserId());
            return ResponseEntity.noContent().build();
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/{ruleId}/analytics")
    @RequireAuth
    public ResponseEntity<PromotionRuleAnalyticsResponse> getRuleAnalytics(
            @PathVariable UUID companyId,
            @PathVariable UUID ruleId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        try {
            return ResponseEntity.ok(pricingReportService.getRuleAnalytics(
                    companyId, ruleId, resolveUserId(), from, to));
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
