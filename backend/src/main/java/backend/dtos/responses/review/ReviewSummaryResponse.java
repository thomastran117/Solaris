package backend.dtos.responses.review;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class ReviewSummaryResponse {
    private UUID productId;
    private double averageRating;
    private long total;
    private long verifiedCount;
    private Map<Integer, Long> distribution;
}
