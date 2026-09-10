package backend.controllers.impl.payments;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.dispute.AddEvidenceRequest;
import backend.dtos.responses.dispute.DisputeCaseDetailResponse;
import backend.dtos.responses.dispute.DisputeCaseResponse;
import backend.dtos.responses.dispute.DisputeEvidenceResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.services.intf.payments.DisputeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Chargeback review surface for the support team. Disputes are raised against the platform's
 * Stripe account rather than an individual merchant, so access is platform-staff only.
 */
@RestController
@RequestMapping("/admin/disputes")
@RequireAuth(roles = {"SUPPORT", "MODERATOR", "ADMIN"})
public class DisputeController {

    private final DisputeService disputeService;

    public DisputeController(DisputeService disputeService) {
        this.disputeService = disputeService;
    }

    /** Open (non-closed) disputes only — the work queue, not the archive. */
    @GetMapping
    public ResponseEntity<PagedResponse<DisputeCaseResponse>> listOpenDisputes(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        try {
            // No caller-supplied sort: the query fixes the order to soonest-deadline-first so
            // the most urgent cases cannot be pushed off page 1.
            Pageable pageable = PageRequest.of(page, size);
            return ResponseEntity.ok(disputeService.getOpenDisputes(pageable));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<DisputeCaseDetailResponse> getDispute(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(disputeService.getDispute(id));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{id}/evidence")
    public ResponseEntity<DisputeEvidenceResponse> addEvidence(
            @PathVariable UUID id,
            @Valid @RequestBody AddEvidenceRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(disputeService.addManualEvidence(id, request, resolveUserId()));
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
