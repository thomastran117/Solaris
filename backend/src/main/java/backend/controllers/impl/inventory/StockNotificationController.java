package backend.controllers.impl.inventory;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.inventory.SubscribeBackInStockRequest;
import backend.dtos.responses.inventory.StockNotificationResponse;
import backend.services.intf.RateLimitService;
import backend.services.intf.inventory.StockNotificationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/stock-notifications")
@RequireAuth
public class StockNotificationController {

    private static final int SUBSCRIBE_LIMIT = 30;
    private static final int SUBSCRIBE_WINDOW_SECONDS = 60;

    private final StockNotificationService stockNotificationService;
    private final RateLimitService rateLimitService;

    public StockNotificationController(StockNotificationService stockNotificationService,
                                       RateLimitService rateLimitService) {
        this.stockNotificationService = stockNotificationService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping
    public ResponseEntity<StockNotificationResponse> subscribe(
            @Valid @RequestBody SubscribeBackInStockRequest request) {
        long userId = resolveUserId();
        rateLimitService.enforce("stock:subscribe", Long.toString(userId), SUBSCRIBE_LIMIT, SUBSCRIBE_WINDOW_SECONDS);
        return ResponseEntity.ok(stockNotificationService.subscribe(userId, request));
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> cancel(@PathVariable long notificationId) {
        stockNotificationService.cancel(resolveUserId(), notificationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<StockNotificationResponse>> list() {
        return ResponseEntity.ok(stockNotificationService.listByUser(resolveUserId()));
    }

    private long resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return ((Number) auth.getPrincipal()).longValue();
    }
}
