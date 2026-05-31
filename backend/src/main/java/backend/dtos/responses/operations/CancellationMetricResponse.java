package backend.dtos.responses.operations;

import backend.models.enums.CancellationReason;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CancellationMetricResponse {
    private long total;
    private List<ReasonCount> byReason;
    private List<DailyPoint> daily;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReasonCount {
        private CancellationReason reason;
        private long count;
    }
}
