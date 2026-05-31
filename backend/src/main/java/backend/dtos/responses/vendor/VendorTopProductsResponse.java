package backend.dtos.responses.vendor;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class VendorTopProductsResponse {
    private UUID vendorId;
    private int windowDays;
    private List<ProductEntry> products;

    public record ProductEntry(
            UUID productId,
            String productName,
            long totalUnitsSold,
            BigDecimal totalRevenue
    ) {}
}
