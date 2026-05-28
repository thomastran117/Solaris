package backend.dtos.requests.order;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignDriverRequest(@NotNull UUID driverUserId) {}
