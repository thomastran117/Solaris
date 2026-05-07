package backend.controllers.impl.inventory;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.inventory.SubscribeBackInStockRequest;
import backend.dtos.responses.inventory.StockNotificationResponse;
import backend.services.intf.inventory.StockNotificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/stock-notifications")
@RequireAuth
public class StockNotificationController {

    private final StockNotificationService stockNotificationService;

    public StockNotificationController(StockNotificationService stockNotificationService) {
        this.stockNotificationService = stockNotificationService;
    }

    @PostMapping
    public ResponseEntity<StockNotificationResponse> subscribe(
            @Valid @RequestBody SubscribeBackInStockRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(stockNotificationService.subscribe(resolveUserId(), request));
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
