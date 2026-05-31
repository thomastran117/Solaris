package backend.dtos.requests.order;

import backend.annotations.safeText.SafeText;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record ReturnOrderRequest(
        /** Optional merchant note about the return. */
        @SafeText @Size(max = 1000) String note,
        /**
         * Optional list of OrderItem IDs to return. When null or empty, all DELIVERED items are
         * returned. When provided, only the specified items are returned.
         */
        List<UUID> itemIds,
        /** When true, restore stock for each returned item. */
        boolean restockItems,
        /** When true, issue a refund via the payment provider. */
        boolean issueRefund
) {}
