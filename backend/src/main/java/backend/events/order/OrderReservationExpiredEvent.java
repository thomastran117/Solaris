package backend.events.order;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OrderReservationExpiredEvent(
        UUID orderId,
        UUID userId,
        String userEmail,
        String userFirstName,
        List<AbandonedItemData> items) {

    public record AbandonedItemData(
            UUID productId,
            String productName,
            String thumbnailUrl,
            BigDecimal unitPrice) {}
}
