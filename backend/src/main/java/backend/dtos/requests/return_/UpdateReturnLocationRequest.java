package backend.dtos.requests.return_;

import backend.annotations.safeText.SafeText;
import jakarta.validation.constraints.Size;

public record UpdateReturnLocationRequest(
        @SafeText @Size(max = 255) String address,
        @SafeText @Size(max = 100) String city,
        @SafeText @Size(max = 100) String country,
        @Size(max = 20) String postalCode,
        @SafeText @Size(max = 100) String name,
        Boolean primary
) {}
