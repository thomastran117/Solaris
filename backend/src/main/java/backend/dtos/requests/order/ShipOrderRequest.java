package backend.dtos.requests.order;

import backend.annotations.safeText.SafeText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ShipOrderRequest(
        /** Required carrier tracking number. */
        @NotBlank @Size(max = 100) String trackingNumber,
        /** Optional carrier name (e.g. "UPS", "FedEx", "USPS"). */
        @SafeText @Size(max = 100) String carrier,
        /** Optional merchant note recorded on the order. */
        @SafeText @Size(max = 1000) String note,
        /**
         * Optional list of OrderItem IDs to ship. When null or empty, all PACKED items are
         * transitioned to SHIPPED. When provided, only the specified items are shipped,
         * enabling partial shipments that set order status to PARTIALLY_FULFILLED.
         */
        List<UUID> itemIds
) {}
