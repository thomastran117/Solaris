package backend.controllers.impl.b2b;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.b2b.MarkInvoicePaidRequest;
import backend.dtos.requests.b2b.VendorQuoteResponseRequest;
import backend.dtos.responses.b2b.B2BInvoiceResponse;
import backend.dtos.responses.b2b.QuoteResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.models.enums.InvoiceStatus;
import backend.models.enums.QuoteStatus;
import backend.services.intf.b2b.B2BInvoiceService;
import backend.services.intf.b2b.B2BQuoteService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Vendor-facing B2B quoting and invoicing endpoints (Feature 12). */
@RestController
@RequestMapping("/companies/{companyId}/b2b")
@RequireAuth
public class CompanyB2BController {

    private final B2BQuoteService quoteService;
    private final B2BInvoiceService invoiceService;

    public CompanyB2BController(B2BQuoteService quoteService, B2BInvoiceService invoiceService) {
        this.quoteService = quoteService;
        this.invoiceService = invoiceService;
    }

    @GetMapping("/quotes")
    public ResponseEntity<PagedResponse<QuoteResponse>> listQuotes(
            @PathVariable UUID companyId,
            @RequestParam(required = false) QuoteStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok(quoteService.listVendorQuotes(companyId, resolveUserId(), status, page, size));
    }

    @PatchMapping("/quotes/{id}/respond")
    public ResponseEntity<QuoteResponse> respond(
            @PathVariable UUID companyId,
            @PathVariable UUID id,
            @Valid @RequestBody VendorQuoteResponseRequest request) {
        return ResponseEntity.ok(quoteService.vendorRespondToQuote(companyId, id, resolveUserId(), request));
    }

    @GetMapping("/invoices")
    public ResponseEntity<PagedResponse<B2BInvoiceResponse>> listInvoices(
            @PathVariable UUID companyId,
            @RequestParam(required = false) InvoiceStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok(invoiceService.listInvoices(companyId, resolveUserId(), status, page, size));
    }

    @GetMapping("/invoices/overdue")
    public ResponseEntity<List<B2BInvoiceResponse>> listOverdueInvoices(@PathVariable UUID companyId) {
        return ResponseEntity.ok(invoiceService.getOverdueInvoices(companyId, resolveUserId()));
    }

    @PostMapping("/invoices/{id}/mark-paid")
    public ResponseEntity<Void> markInvoicePaid(
            @PathVariable UUID companyId,
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) MarkInvoicePaidRequest request) {
        String reference = request != null ? request.paymentReference() : null;
        invoiceService.markInvoicePaid(companyId, id, resolveUserId(), reference);
        return ResponseEntity.noContent().build();
    }

    private UUID resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (UUID) auth.getPrincipal();
    }
}
