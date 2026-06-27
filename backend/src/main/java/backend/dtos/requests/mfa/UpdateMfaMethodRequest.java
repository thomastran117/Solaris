package backend.dtos.requests.mfa;

import jakarta.validation.constraints.NotNull;

public record UpdateMfaMethodRequest(
        @NotNull(message = "enabled is required")
        Boolean enabled
) {}
