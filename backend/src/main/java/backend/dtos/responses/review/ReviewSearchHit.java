package backend.dtos.responses.review;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@AllArgsConstructor
public class ReviewSearchHit {
    private UUID id;
    private UUID productId;
    private UUID reviewerId;
    private String reviewerName;
    private int rating;
    private String title;
    private String body;
    private boolean verifiedPurchase;
    private boolean hasMedia;
    private int helpfulCount;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
    /** Highlight fragments keyed by field name (title, body). Empty if no highlights matched. */
    private List<String> titleHighlights;
    private List<String> bodyHighlights;
    private double relevanceScore;
}
