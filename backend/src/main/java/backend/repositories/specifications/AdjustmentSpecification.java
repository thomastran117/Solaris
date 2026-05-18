package backend.repositories.specifications;

import backend.models.core.InventoryAdjustment;
import backend.models.enums.AdjustmentReason;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AdjustmentSpecification {

    public static Specification<InventoryAdjustment> withFilters(
            long companyId,
            AdjustmentReason reason,
            Instant from,
            Instant to,
            Long productId,
            UUID userId) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Join path: adjustment -> product -> company
            var product = root.join("product", JoinType.INNER);
            var company = product.join("company", JoinType.INNER);
            predicates.add(cb.equal(company.get("id"), companyId));

            if (reason != null) {
                predicates.add(cb.equal(root.get("reason"), reason));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            if (productId != null) {
                predicates.add(cb.equal(product.get("id"), productId));
            }
            if (userId != null) {
                predicates.add(cb.equal(
                        root.join("adjustedBy", JoinType.LEFT).get("id"), userId));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
