package backend.dtos.responses.product;

import java.time.Instant;
import java.util.UUID;

public record ProductImageResponse(
        UUID id,
        String imageUrl,
        int displayOrder,
        Instant createdAt
) {}
