package backend.dtos.responses.segment;

import java.util.UUID;
import java.time.Instant;

public record CustomerSegmentResponse(
        UUID id,
        String code,
        String name,
        String description,
        Instant createdAt,
        Instant updatedAt
) {}
