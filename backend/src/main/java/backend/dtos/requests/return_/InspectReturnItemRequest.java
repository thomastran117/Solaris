package backend.dtos.requests.return_;

import backend.models.enums.ReturnItemCondition;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record InspectReturnItemRequest(
        @NotNull UUID returnItemId,
        @NotNull ReturnItemCondition condition,
        boolean restock
) {}
