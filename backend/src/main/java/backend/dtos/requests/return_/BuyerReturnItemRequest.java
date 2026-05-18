package backend.dtos.requests.return_;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record BuyerReturnItemRequest(
        @NotNull UUID orderItemId,
        @Min(1) int quantityToReturn
) {}
