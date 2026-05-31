package backend.dtos.responses.vendor;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class VendorAnalyticsSummaryResponse {
    private UUID vendorId;
    private UUID marketplaceId;
    private int windowDays;
    private Instant from;
    private Instant to;
    private long totalSubOrders;
    private BigDecimal totalGrossRevenue;
    private BigDecimal totalCommission;
    private BigDecimal totalNetRevenue;
    private BigDecimal avgOrderValue;
    private double cancellationRate;
    private double refundRate;
    private double lateShipmentRate;
    private Double avgShipHours;
}
