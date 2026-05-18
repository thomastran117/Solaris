package backend.dtos.responses.product;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class VendorStorefrontResponse {
    private UUID vendorId;
    private UUID marketplaceId;
    private String vendorCompanyName;
    private String vendorDescription;
    private String vendorLogoUrl;
    private String vendorTier;
    private String vendorStatus;
    private List<MarketplaceCatalogProductResponse> featuredProducts;
    private long totalListedProducts;
}
