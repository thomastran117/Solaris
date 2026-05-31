package backend.dtos.responses.operations;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OperationsSummaryResponse {
    private UUID companyId;
    private int lookbackDays;
    private Instant from;
    private Instant to;

    private DurationMetricResponse fulfillment;
    private DurationMetricResponse refunds;
    private DurationMetricResponse pickDelays;
    private StockoutMetricResponse stockouts;
    private SupplierLatenessMetricResponse supplierLateness;
    private CancellationMetricResponse cancellations;
}
