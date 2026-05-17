package backend.dtos.responses.review;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

@Getter
@AllArgsConstructor
public class ReviewSummaryResponse {
    private Long productId;
    private double averageRating;
    private long total;
    private long verifiedCount;
    private Map<Integer, Long> distribution;
}
