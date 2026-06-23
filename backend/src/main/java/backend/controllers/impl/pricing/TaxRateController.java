package backend.controllers.impl.pricing;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.tax.CreateTaxRateRequest;
import backend.dtos.requests.tax.UpdateTaxRateRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.tax.TaxRateResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.services.intf.pricing.TaxRateAdminService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Platform-admin CRUD for destination-based sales-tax rates. */
@RestController
@RequestMapping("/admin/tax-rates")
public class TaxRateController {

    private final TaxRateAdminService taxRateAdminService;

    public TaxRateController(TaxRateAdminService taxRateAdminService) {
        this.taxRateAdminService = taxRateAdminService;
    }

    @GetMapping
    @RequireAuth(roles = {"ADMIN"})
    public ResponseEntity<PagedResponse<TaxRateResponse>> listRates(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        try {
            return ResponseEntity.ok(taxRateAdminService.listRates(page, size));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @GetMapping("/{id}")
    @RequireAuth(roles = {"ADMIN"})
    public ResponseEntity<TaxRateResponse> getRate(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(taxRateAdminService.getRate(id));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping
    @RequireAuth(roles = {"ADMIN"})
    public ResponseEntity<TaxRateResponse> createRate(@Valid @RequestBody CreateTaxRateRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(taxRateAdminService.createRate(request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PatchMapping("/{id}")
    @RequireAuth(roles = {"ADMIN"})
    public ResponseEntity<TaxRateResponse> updateRate(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTaxRateRequest request) {
        try {
            return ResponseEntity.ok(taxRateAdminService.updateRate(id, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @DeleteMapping("/{id}")
    @RequireAuth(roles = {"ADMIN"})
    public ResponseEntity<Void> deleteRate(@PathVariable UUID id) {
        try {
            taxRateAdminService.deleteRate(id);
            return ResponseEntity.noContent().build();
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }
}
