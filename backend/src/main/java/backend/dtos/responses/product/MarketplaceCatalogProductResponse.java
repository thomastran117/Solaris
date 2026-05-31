package backend.dtos.responses.product;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class MarketplaceCatalogProductResponse {
    private UUID id;
    private UUID companyId;
    private UUID marketplaceId;
    private UUID vendorId;
    private String vendorName;
    private String vendorTier;
    private String name;
    private String description;
    private String sku;
    private BigDecimal price;
    private BigDecimal compareAtPrice;
    private String currency;
    private String category;
    private String brand;
    private String tags;
    private String thumbnailUrl;
    private List<ProductImageResponse> images;
    private List<ProductVariantResponse> variants;
    private Integer stock;
    private String status;
    private boolean featured;
    private boolean purchasable;
    private Instant createdAt;
    private Instant updatedAt;
    private ActivePromotionSummary activePromotion;
}
