package backend.controllers.impl.returns;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.return_.CreateReturnLocationRequest;
import backend.dtos.requests.return_.UpdateReturnLocationRequest;
import backend.dtos.responses.return_.ReturnLocationResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.services.intf.returns.ReturnLocationService;

import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/companies/{companyId}/return-locations")
@RequireAuth
public class CompanyReturnLocationController {

    private final ReturnLocationService returnLocationService;

    public CompanyReturnLocationController(ReturnLocationService returnLocationService) {
        this.returnLocationService = returnLocationService;
    }

    @GetMapping
    public ResponseEntity<List<ReturnLocationResponse>> getReturnLocations(@PathVariable UUID companyId) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(returnLocationService.getReturnLocations(companyId, userId));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping
    public ResponseEntity<ReturnLocationResponse> createReturnLocation(
            @PathVariable UUID companyId,
            @RequestBody @Valid CreateReturnLocationRequest request) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(returnLocationService.createReturnLocation(companyId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PatchMapping("/{locationId}")
    public ResponseEntity<ReturnLocationResponse> updateReturnLocation(
            @PathVariable UUID companyId,
            @PathVariable UUID locationId,
            @RequestBody @Valid UpdateReturnLocationRequest request) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(returnLocationService.updateReturnLocation(locationId, companyId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @DeleteMapping("/{locationId}")
    public ResponseEntity<Void> deleteReturnLocation(
            @PathVariable UUID companyId,
            @PathVariable UUID locationId) {
        try {
            UUID userId = resolveUserId();
            returnLocationService.deleteReturnLocation(locationId, companyId, userId);
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
