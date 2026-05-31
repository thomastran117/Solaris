package backend.dtos.responses.product;

import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.search.SearchFacets;
import lombok.Getter;
import org.springframework.data.domain.Page;

@Getter
public class CatalogSearchResponse extends PagedResponse<MarketplaceCatalogProductResponse> {
    private final SearchFacets facets;

    public CatalogSearchResponse(Page<MarketplaceCatalogProductResponse> page, SearchFacets facets) {
        super(page);
        this.facets = facets;
    }
}
