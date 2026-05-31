package backend.dtos.responses.product;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

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
    private Integer stock;
    private BigDecimal weight;
    private String weightUnit;
    private String status;
    private boolean featured;
    private Instant createdAt;
    private Instant updatedAt;
}
