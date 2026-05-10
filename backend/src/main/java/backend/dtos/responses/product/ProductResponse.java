package backend.dtos.responses.product;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Getter
@AllArgsConstructor
public class ProductResponse {
    private Long id;
    private Long companyId;
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
    private List<ProductOptionResponse> options;
    private List<ProductVariantResponse> variants;
    private List<ProductAttributeResponse> attributes;
    private Integer stock;
    private Integer lowStockThreshold;
    private BigDecimal weight;
    private String weightUnit;
    private String status;
    private Instant scheduledPublishAt;
    private Instant publishedAt;
    private boolean featured;
    private boolean purchasable;
    private boolean listed;
    private Instant createdAt;
    private Instant updatedAt;
    private Double avgRating;
    private Long reviewCount;
    private ActivePromotionSummary activePromotion;
}
