package backend.services.intf.products;

import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.review.ReviewSearchHit;

import java.util.List;
import java.util.UUID;

public interface ReviewSearchService {
    PagedResponse<ReviewSearchHit> search(
            UUID companyId,
            UUID productId,
            String q,
            List<Integer> ratings,
            Boolean verifiedOnly,
            String sort,
            int page,
            int size);
}
