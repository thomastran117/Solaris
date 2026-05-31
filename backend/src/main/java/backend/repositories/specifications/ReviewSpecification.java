package backend.repositories.specifications;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import backend.models.core.ProductReview;
import backend.models.core.ReviewMedia;
import backend.models.enums.ReviewStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class ReviewSpecification {

    private ReviewSpecification() {}

    /**
     * Filters published reviews for a product. Optional rating set narrows to specific star counts;
     * {@code verifiedOnly} requires verified_purchase=TRUE; {@code hasMedia} restricts to reviews
     * with at least one photo via a correlated subquery against {@link ReviewMedia}.
     */
    public static Specification<ProductReview> withFilters(
            UUID productId,
            Collection<Integer> ratings,
            Boolean verifiedOnly,
            Boolean hasMedia) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("product").get("id"), productId));
            predicates.add(cb.equal(root.get("status"), ReviewStatus.PUBLISHED));

            if (ratings != null && !ratings.isEmpty()) {
                predicates.add(root.get("rating").in(ratings));
            }
            if (Boolean.TRUE.equals(verifiedOnly)) {
                predicates.add(cb.isTrue(root.get("verifiedPurchase")));
            }
            if (Boolean.TRUE.equals(hasMedia) && query != null) {
                Subquery<Long> sub = query.subquery(Long.class);
                var media = sub.from(ReviewMedia.class);
                sub.select(cb.literal(1L))
                        .where(cb.equal(media.get("reviewId"), root.get("id")));
                predicates.add(cb.exists(sub));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
