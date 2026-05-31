package backend.dtos.responses.product;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class BundleResponse {
    private UUID id;
    private UUID companyId;
    private String name;
    private String description;
    private String thumbnailUrl;
    private BigDecimal price;
    private BigDecimal compareAtPrice;
    private String currency;
    private String status;
    private boolean listed;
    private boolean preorderEnabled;
    private Instant preorderExpectedDate;
    private List<BundleItemResponse> items;
    private Instant createdAt;
    private Instant updatedAt;
}
