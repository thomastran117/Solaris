package backend.dtos.responses.return_;

import java.math.BigDecimal;
import java.util.UUID;

public record ReturnItemResponse(
        UUID id,
        UUID orderItemId,
        String productName,
        UUID variantId,
        String variantTitle,
        int quantityReturned,
        BigDecimal unitPrice,
        boolean stockRestored,
        String condition    // null until inspectReturn() is called
) {}
