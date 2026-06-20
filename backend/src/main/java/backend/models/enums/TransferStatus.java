package backend.models.enums;

/**
 * Lifecycle of an {@link backend.models.core.InventoryTransfer}.
 *
 * <pre>
 *   PENDING ──dispatch──▶ IN_TRANSIT ──receive──▶ RECEIVED
 *      │
 *      └──cancel──▶ CANCELLED
 * </pre>
 *
 * Cancellation is only permitted from PENDING — once goods are IN_TRANSIT or RECEIVED the
 * transfer is terminal for cancellation purposes.
 */
public enum TransferStatus {
    PENDING,
    IN_TRANSIT,
    RECEIVED,
    CANCELLED
}
