package backend.dtos.responses.product;

import java.util.UUID;
import backend.models.enums.CompanyRole;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

/**
 * Unified timeline row for a product's history. {@code kind} discriminates between
 * field-level catalog changes ({@code FIELD_CHANGE}) and stock movements
 * ({@code INVENTORY_ADJUSTMENT}); only the fields relevant to that {@code kind} are populated.
 */
@Getter
@AllArgsConstructor
public class ProductHistoryEntryResponse {

    public enum Kind { FIELD_CHANGE, INVENTORY_ADJUSTMENT }

    private Kind kind;
    private UUID id;
    private UUID productId;
    private UUID variantId;
    private UUID actorUserId;
    private CompanyRole actorRole;
    private Instant occurredAt;

    // FIELD_CHANGE
    private String fieldName;
    private String oldValue;
    private String newValue;
    private String changeType;       // CREATED | UPDATED | DELETED
    private String source;           // USER | SYSTEM | REVERT
    private UUID revertedFromLogId;

    // INVENTORY_ADJUSTMENT
    private Integer delta;
    private Integer previousStock;
    private Integer newStock;
    private String reason;
    private String note;
    private UUID orderId;
}
