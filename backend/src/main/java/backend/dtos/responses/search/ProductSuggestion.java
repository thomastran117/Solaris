package backend.dtos.responses.search;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductSuggestion(
        UUID id,
        String name,
        BigDecimal price,
        BigDecimal discountedPrice,
        String thumbnailUrl
) {}
