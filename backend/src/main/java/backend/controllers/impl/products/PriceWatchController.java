package backend.controllers.impl.products;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.responses.products.PriceWatcherResponse;
import backend.services.intf.RateLimitService;
import backend.services.intf.products.PriceWatchService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequireAuth
public class PriceWatchController {

    private static final int WATCH_LIMIT = 30;
    private static final int WATCH_WINDOW_SECONDS = 60;

    private final PriceWatchService priceWatchService;
    private final RateLimitService rateLimitService;

    public PriceWatchController(PriceWatchService priceWatchService,
                                RateLimitService rateLimitService) {
        this.priceWatchService = priceWatchService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping("/products/{productId}/price-watch")
    public ResponseEntity<PriceWatcherResponse> watch(@PathVariable UUID productId) {
        UUID userId = resolveUserId();
        rateLimitService.enforce("price-watch:watch", userId.toString(), WATCH_LIMIT, WATCH_WINDOW_SECONDS);
        return ResponseEntity.ok(priceWatchService.watchProduct(userId, productId));
    }

    @DeleteMapping("/products/{productId}/price-watch")
    public ResponseEntity<Void> unwatch(@PathVariable UUID productId) {
        priceWatchService.unwatchProduct(resolveUserId(), productId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/price-watches")
    public ResponseEntity<Page<PriceWatcherResponse>> list(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(priceWatchService.getWatchedProducts(resolveUserId(), pageable));
    }

    private UUID resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (UUID) auth.getPrincipal();
    }
}
