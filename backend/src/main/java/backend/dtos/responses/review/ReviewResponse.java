package backend.dtos.responses.review;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@AllArgsConstructor
public class ReviewResponse {
    private UUID id;
    private UUID productId;
    private UUID reviewerId;
    private String reviewerFirstName;
    private String reviewerLastName;
    private int rating;
    private String title;
    private String body;
    private String status;
    private boolean verifiedPurchase;
    private int helpfulCount;
    private List<ReviewMediaResponse> media;
    private Instant createdAt;
    private Instant updatedAt;
}
