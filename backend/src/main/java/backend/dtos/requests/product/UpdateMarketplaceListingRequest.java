package backend.dtos.requests.product;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class UpdateMarketplaceListingRequest {

    @NotNull(message = "Marketplace ID is required")
    private UUID marketplaceId;

    @NotNull(message = "Listed flag is required")
    private Boolean listed;
}
