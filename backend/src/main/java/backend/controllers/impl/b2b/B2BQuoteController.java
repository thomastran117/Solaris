package backend.controllers.impl.b2b;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.b2b.CreateQuoteRequest;
import backend.dtos.responses.b2b.QuoteResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.order.OrderResponse;
import backend.services.intf.b2b.B2BQuoteService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Buyer-facing B2B quoting endpoints (Feature 12). */
@RestController
@RequestMapping("/b2b/quotes")
@RequireAuth
public class B2BQuoteController {

    private final B2BQuoteService quoteService;

    public B2BQuoteController(B2BQuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @PostMapping
    public ResponseEntity<QuoteResponse> requestQuote(
            @RequestParam @NotNull UUID vendorCompanyId,
            @Valid @RequestBody CreateQuoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(quoteService.requestQuote(resolveUserId(), vendorCompanyId, request));
    }

    @GetMapping
    public ResponseEntity<PagedResponse<QuoteResponse>> listQuotes(
            @RequestParam(required = false) backend.models.enums.QuoteStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok(quoteService.listBuyerQuotes(resolveUserId(), status, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<QuoteResponse> getQuote(@PathVariable UUID id) {
        return ResponseEntity.ok(quoteService.getBuyerQuote(id, resolveUserId()));
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<OrderResponse> acceptQuote(@PathVariable UUID id) {
        return ResponseEntity.ok(quoteService.buyerAcceptQuote(id, resolveUserId()));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> rejectQuote(@PathVariable UUID id) {
        quoteService.buyerRejectQuote(id, resolveUserId());
        return ResponseEntity.noContent().build();
    }

    private UUID resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (UUID) auth.getPrincipal();
    }
}
