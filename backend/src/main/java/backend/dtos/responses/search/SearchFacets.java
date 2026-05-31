package backend.dtos.responses.search;

import java.util.List;

public record SearchFacets(
        List<FacetBucket> categories,
        List<FacetBucket> brands,
        List<PriceRangeBucket> priceRanges
) {}
