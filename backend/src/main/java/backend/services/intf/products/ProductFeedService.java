package backend.services.intf.products;

import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.product.MarketplaceCatalogProductResponse;

import java.util.UUID;

public interface ProductFeedService {
    PagedResponse<MarketplaceCatalogProductResponse> getFeed(
            UUID marketplaceId, UUID userId, int page, int size);
}
