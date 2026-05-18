package backend.services.intf.marketplace;

import java.util.UUID;
import backend.dtos.requests.marketplace.CreateMarketplaceRequest;
import backend.dtos.requests.marketplace.UpdateMarketplaceRequest;
import backend.dtos.responses.marketplace.MarketplaceProfileResponse;

public interface MarketplaceService {

    MarketplaceProfileResponse createMarketplace(UUID ownerId, UUID companyId, CreateMarketplaceRequest request);

    MarketplaceProfileResponse getMarketplace(UUID marketplaceId);

    MarketplaceProfileResponse updateMarketplace(UUID marketplaceId, UUID ownerId, UpdateMarketplaceRequest request);
}
