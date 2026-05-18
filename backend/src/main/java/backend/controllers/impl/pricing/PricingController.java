package backend.controllers.impl.pricing;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import backend.dtos.requests.pricing.PricingQuoteRequest;
import backend.dtos.responses.pricing.PricingQuoteResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.InternalServerErrorException;
import backend.services.intf.products.PricingQuoteService;

import jakarta.validation.Valid;

/**
 * Stateless pricing quote endpoint. Auth is optional — anonymous callers skip
 * segment-targeted rules. The endpoint never mutates rule usage counters.
 */
@RestController
@RequestMapping("/pricing")
public class PricingController {

    private final PricingQuoteService quoteService;

    public PricingController(PricingQuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @PostMapping("/quote")
    public ResponseEntity<PricingQuoteResponse> quote(@Valid @RequestBody PricingQuoteRequest request) {
        try {
            return ResponseEntity.ok(quoteService.quote(request, resolveUserIdOrNull()));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    private UUID resolveUserIdOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof UUID uuid) return uuid;
        return null;
    }
}
