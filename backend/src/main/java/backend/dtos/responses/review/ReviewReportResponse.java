package backend.dtos.responses.review;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@AllArgsConstructor
public class ReviewReportResponse {
    private Long id;
    private Long reviewId;
    private Long reporterId;
    private String reason;
    private String detail;
    private String status;
    private Instant createdAt;
    private Instant resolvedAt;
    private Long resolvedBy;

    // Review snapshot so moderators can act without an extra round-trip
    private Long productId;
    private Long reviewerId;
    private String reviewerName;
    private int rating;
    private String reviewTitle;
    private String reviewBody;
    private String reviewStatus;
    private int reportCount;
    private List<ReviewMediaResponse> media;
}
