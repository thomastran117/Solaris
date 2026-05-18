package backend.dtos.responses.review;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@AllArgsConstructor
public class ReviewReportResponse {
    private UUID id;
    private UUID reviewId;
    private UUID reporterId;
    private String reason;
    private String detail;
    private String status;
    private Instant createdAt;
    private Instant resolvedAt;
    private UUID resolvedBy;

    // Review snapshot so moderators can act without an extra round-trip
    private UUID productId;
    private UUID reviewerId;
    private String reviewerName;
    private int rating;
    private String reviewTitle;
    private String reviewBody;
    private String reviewStatus;
    private int reportCount;
    private List<ReviewMediaResponse> media;
}
