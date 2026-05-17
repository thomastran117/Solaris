package backend.services.intf.products;

import backend.dtos.responses.review.HelpfulVoteResponse;

public interface ReviewVoteService {
    HelpfulVoteResponse voteHelpful(long reviewId, long userId);
    HelpfulVoteResponse removeHelpful(long reviewId, long userId);
}
