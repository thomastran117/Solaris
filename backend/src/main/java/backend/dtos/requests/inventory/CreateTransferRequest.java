package backend.dtos.requests.inventory;

import backend.annotations.safeRichText.SafeRichText;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CreateTransferRequest {

    @NotNull(message = "Product id is required")
    private UUID productId;

    @NotNull(message = "Source location id is required")
    private UUID fromLocationId;

    @NotNull(message = "Destination location id is required")
    private UUID toLocationId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    @Size(max = 500, message = "Notes must not exceed 500 characters")
    @SafeRichText
    private String notes;

    @AssertTrue(message = "Source and destination locations must be different")
    public boolean isDistinctLocations() {
        return fromLocationId == null || toLocationId == null || !fromLocationId.equals(toLocationId);
    }
}
