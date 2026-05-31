package backend.dtos.responses.collection;

import java.time.Instant;
import java.util.UUID;

/**
 * Admin/marketplace view of a {@code Collection}. Carries enum names as Strings so the
 * frontend can string-compare without bundling enum schemas.
 */
public record CollectionResponse(
        UUID id,
        UUID companyId,
        String name,
        String slug,
        String description,
        String imageUrl,
        String type,
        String status,
        boolean featured,
        Integer featuredRank,
        String rulesJson,
        long productCount,
        Instant lastMaterialisedAt,
        Instant createdAt,
        Instant updatedAt
) {}
