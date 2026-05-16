package backend.dtos.requests.return_;

import backend.annotations.safeText.SafeText;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record InspectReturnRequest(
        @NotEmpty List<@Valid InspectReturnItemRequest> items,
        @SafeText @Size(max = 1000) String merchantNote
) {}
