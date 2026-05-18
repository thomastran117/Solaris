package backend.dtos.responses.return_;

import java.time.Instant;
import java.util.UUID;

public record ReturnLocationResponse(
        UUID id,
        UUID companyId,
        String name,
        String address,
        String city,
        String country,
        String postalCode,
        boolean primary,
        Instant createdAt,
        Instant updatedAt
) {}
