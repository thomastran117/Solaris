package backend.events.products;

import java.math.BigDecimal;
import java.util.UUID;

public record PriceDropAlertEvent(UUID productId, BigDecimal oldPrice, BigDecimal newPrice) {}
