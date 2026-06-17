package backend.dtos.responses.product;

import java.util.Map;
import java.util.UUID;

/**
 * One row of a comparison matrix for a single attribute name. {@code valuesByProductId} maps
 * each compared product's id to its value for this attribute; a product that lacks the
 * attribute maps to {@code null}, keeping the row aligned across all columns.
 */
public record ComparisonRow(
        String attributeName,
        Map<UUID, String> valuesByProductId
) {}
