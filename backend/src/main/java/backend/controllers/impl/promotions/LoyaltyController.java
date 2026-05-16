package backend.controllers.impl.promotions;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.loyalty.AdjustPointsRequest;
import backend.dtos.requests.loyalty.CreateLoyaltyPolicyRequest;
import backend.dtos.requests.loyalty.CreateLoyaltyTierRequest;
import backend.dtos.requests.loyalty.IssueBonusRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.loyalty.LoyaltyAccountResponse;
import backend.dtos.responses.loyalty.LoyaltyPolicyResponse;
import backend.dtos.responses.loyalty.LoyaltyRedemptionQuoteResponse;
import backend.dtos.responses.loyalty.LoyaltyTierResponse;
import backend.dtos.responses.loyalty.LoyaltyTransactionResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.promotions.LoyaltyService;

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
public class LoyaltyController {

    private final LoyaltyService loyaltyService;
    private final CompanyAccessService companyAccessService;

    public LoyaltyController(LoyaltyService loyaltyService,
                             CompanyAccessService companyAccessService) {
        this.loyaltyService = loyaltyService;
        this.companyAccessService = companyAccessService;
    }

    // -------------------------------------------------------------------------
    // Customer self-service
    // -------------------------------------------------------------------------

    @GetMapping("/loyalty/account")
    public ResponseEntity<LoyaltyAccountResponse> getAccount(@RequestParam long companyId) {
        try {
            long userId = resolveUserId();
            return ResponseEntity.ok(loyaltyService.getAccount(userId, companyId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/loyalty/transactions")
    public ResponseEntity<PagedResponse<LoyaltyTransactionResponse>> getTransactions(
            @RequestParam long companyId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        try {
            long userId = resolveUserId();
            return ResponseEntity.ok(loyaltyService.getTransactions(userId, companyId, page, size));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/loyalty/quote")
    public ResponseEntity<LoyaltyRedemptionQuoteResponse> getRedemptionQuote(
            @RequestParam long companyId,
            @RequestParam int points) {
        try {
            long userId = resolveUserId();
            return ResponseEntity.ok(loyaltyService.getRedemptionQuote(userId, companyId, points));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    // -------------------------------------------------------------------------
    // Policy management (company owner)
    // -------------------------------------------------------------------------

    @GetMapping("/companies/{companyId}/loyalty/policy")
    public ResponseEntity<LoyaltyPolicyResponse> getPolicy(@PathVariable long companyId) {
        try {
            companyAccessService.requireAnyAccess(companyId, resolveUserId());
            return ResponseEntity.ok(loyaltyService.getPolicy(companyId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/companies/{companyId}/loyalty/policy")
    public ResponseEntity<LoyaltyPolicyResponse> createOrUpdatePolicy(
            @PathVariable long companyId,
            @Valid @RequestBody CreateLoyaltyPolicyRequest request) {
        try {
            long userId = resolveUserId();
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(loyaltyService.createOrUpdatePolicy(companyId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    // -------------------------------------------------------------------------
    // Tier management (company owner)
    // -------------------------------------------------------------------------

    @GetMapping("/companies/{companyId}/loyalty/tiers")
    public ResponseEntity<List<LoyaltyTierResponse>> listTiers(@PathVariable long companyId) {
        try {
            companyAccessService.requireAnyAccess(companyId, resolveUserId());
            return ResponseEntity.ok(loyaltyService.listTiers(companyId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/companies/{companyId}/loyalty/tiers")
    public ResponseEntity<LoyaltyTierResponse> createTier(
            @PathVariable long companyId,
            @Valid @RequestBody CreateLoyaltyTierRequest request) {
        try {
            long userId = resolveUserId();
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(loyaltyService.createTier(companyId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PutMapping("/companies/{companyId}/loyalty/tiers/{tierId}")
    public ResponseEntity<LoyaltyTierResponse> updateTier(
            @PathVariable long companyId,
            @PathVariable long tierId,
            @Valid @RequestBody CreateLoyaltyTierRequest request) {
        try {
            long userId = resolveUserId();
            return ResponseEntity.ok(loyaltyService.updateTier(tierId, companyId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    // -------------------------------------------------------------------------
    // Operator actions
    // -------------------------------------------------------------------------

    @PostMapping("/companies/{companyId}/loyalty/bonus")
    public ResponseEntity<LoyaltyTransactionResponse> issueBonus(
            @PathVariable long companyId,
            @Valid @RequestBody IssueBonusRequest request) {
        try {
            long userId = resolveUserId();
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(loyaltyService.issueBonus(companyId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/companies/{companyId}/loyalty/accounts/{accountId}/adjust")
    public ResponseEntity<LoyaltyTransactionResponse> adjustPoints(
            @PathVariable long companyId,
            @PathVariable long accountId,
            @Valid @RequestBody AdjustPointsRequest request) {
        try {
            long userId = resolveUserId();
            return ResponseEntity.ok(loyaltyService.adjustPoints(accountId, companyId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    private long resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return ((Number) auth.getPrincipal()).longValue();
    }
}
