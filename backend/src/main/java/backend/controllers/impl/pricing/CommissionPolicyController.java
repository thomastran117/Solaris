package backend.controllers.impl.pricing;

import java.util.UUID;
import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.marketplace.CreateCommissionPolicyRequest;
import backend.dtos.responses.marketplace.CommissionPolicyResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.services.intf.pricing.CommissionPolicyService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/marketplaces/{marketplaceId}/commission-policies")
@RequireAuth
public class CommissionPolicyController {

    private final CommissionPolicyService commissionPolicyService;

    public CommissionPolicyController(CommissionPolicyService commissionPolicyService) {
        this.commissionPolicyService = commissionPolicyService;
    }

    @GetMapping
    public ResponseEntity<List<CommissionPolicyResponse>> list(@PathVariable UUID marketplaceId) {
        try {
            return ResponseEntity.ok(commissionPolicyService.listPolicies(marketplaceId, resolveUserId()));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping
    public ResponseEntity<CommissionPolicyResponse> create(
            @PathVariable UUID marketplaceId,
            @Valid @RequestBody CreateCommissionPolicyRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(commissionPolicyService.createPolicy(marketplaceId, resolveUserId(), request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @DeleteMapping("/{policyId}")
    public ResponseEntity<Void> delete(
            @PathVariable UUID marketplaceId,
            @PathVariable UUID policyId) {
        try {
            commissionPolicyService.deletePolicy(policyId, marketplaceId, resolveUserId());
            return ResponseEntity.noContent().build();
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
