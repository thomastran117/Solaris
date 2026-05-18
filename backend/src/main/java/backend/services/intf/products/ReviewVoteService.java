package backend.services.intf.products;

import java.util.UUID;
import backend.dtos.responses.review.HelpfulVoteResponse;

public interface ReviewVoteService {
    HelpfulVoteResponse voteHelpful(UUID reviewId, UUID userId);
    HelpfulVoteResponse removeHelpful(UUID reviewId, UUID userId);
}
