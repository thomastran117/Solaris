package backend.dtos.responses.review;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class ReviewResponse {
    private Long id;
    private Long productId;
    private Long reviewerId;
    private String reviewerFirstName;
    private String reviewerLastName;
    private int rating;
    private String title;
    private String body;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
}
