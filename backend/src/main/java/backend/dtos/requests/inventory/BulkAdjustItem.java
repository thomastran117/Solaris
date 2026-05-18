package backend.dtos.requests.inventory;

import backend.annotations.safeRichText.SafeRichText;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import backend.models.enums.AdjustmentReason;

import java.util.UUID;

@Getter
@Setter
public class BulkAdjustItem {

    @NotNull(message = "Product ID is required")
    private UUID productId;

    /** Positive = increase stock, negative = decrease. Zero is invalid. */
    @NotNull(message = "Delta is required")
    private Integer delta;

    @jakarta.validation.constraints.AssertTrue(message = "Delta must not be zero")
    public boolean isDeltaNonZero() {
        return delta == null || delta != 0;
    }

    @NotNull(message = "Reason is required")
    private AdjustmentReason reason;

    @Size(max = 500, message = "Note must not exceed 500 characters")
    @SafeRichText
    private String note;
}
