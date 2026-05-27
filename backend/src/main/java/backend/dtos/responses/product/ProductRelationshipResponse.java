package backend.dtos.responses.product;

import backend.models.enums.ProductRelationshipType;

import java.util.UUID;

public record ProductRelationshipResponse(
        UUID targetProductId,
        String targetProductName,
        String targetProductSku,
        String targetProductThumbnailUrl,
        ProductRelationshipType type,
        String note,
        int displayOrder
) {}
