package backend.dtos.responses.vendor;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class VendorRevenueResponse {
    private UUID vendorId;
    private int windowDays;
    private Instant from;
    private Instant to;
    private BigDecimal totalGross;
    private BigDecimal totalCommission;
    private BigDecimal totalNet;
    private List<DailyRevenuePoint> daily;

    public record DailyRevenuePoint(
            LocalDate day,
            BigDecimal gross,
            BigDecimal commission,
            BigDecimal net,
            long orderCount
    ) {}
}
