package backend.dtos.requests.return_;

import backend.models.enums.ReturnReason;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BuyerInitiateReturnRequest(
        @NotEmpty List<@Valid BuyerReturnItemRequest> items,
        @NotNull ReturnReason reason,
        @NotBlank @Size(max = 1000) String buyerNote,
        @Size(max = 5) List<String> evidenceUrls
) {}
