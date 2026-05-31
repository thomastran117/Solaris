package backend.controllers.impl.returns;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.return_.InspectReturnRequest;
import backend.dtos.requests.return_.MerchantApproveReturnRequest;
import backend.dtos.requests.return_.MerchantRejectReturnRequest;
import backend.dtos.responses.return_.ReturnResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.services.intf.returns.ReturnService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/companies/{companyId}/returns")
@RequireAuth
public class CompanyReturnController {

    private final ReturnService returnService;

    public CompanyReturnController(ReturnService returnService) {
        this.returnService = returnService;
    }

    @GetMapping("/{returnId}")
    public ResponseEntity<ReturnResponse> getCompanyReturn(
            @PathVariable UUID companyId,
            @PathVariable UUID returnId) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(returnService.getCompanyReturn(returnId, companyId, userId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{returnId}/approve")
    public ResponseEntity<ReturnResponse> approveReturn(
            @PathVariable UUID companyId,
            @PathVariable UUID returnId,
            @RequestBody @Valid MerchantApproveReturnRequest request) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(returnService.approveReturn(returnId, companyId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{returnId}/reject")
    public ResponseEntity<ReturnResponse> rejectReturn(
            @PathVariable UUID companyId,
            @PathVariable UUID returnId,
            @RequestBody @Valid MerchantRejectReturnRequest request) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(returnService.rejectReturn(returnId, companyId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/{returnId}/inspect")
    public ResponseEntity<ReturnResponse> inspectReturn(
            @PathVariable UUID companyId,
            @PathVariable UUID returnId,
            @RequestBody @Valid InspectReturnRequest request) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(returnService.inspectReturn(returnId, companyId, userId, request));
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
