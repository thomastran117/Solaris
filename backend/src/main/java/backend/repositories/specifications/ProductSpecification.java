package backend.repositories.specifications;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import backend.models.core.Product;
import backend.models.enums.ProductStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ProductSpecification {

    private ProductSpecification() {}

    /**
     * Builds a dynamic Specification from optional filter values.
     *
     * @param companyId  required — scopes results to a single company
     * @param q          free-text search across name, description, brand, and category
     * @param category   exact match on category
     * @param brand      exact match on brand
     * @param minPrice   inclusive lower bound on price
     * @param maxPrice   inclusive upper bound on price
     * @param featured   when non-null, filters by featured flag
     * @param status     defaults to ACTIVE when null
     */
    public static Specification<Product> withFilters(
            long companyId,
            String q,
            String category,
            String brand,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean featured,
            ProductStatus status) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("company").get("id"), companyId));

            if (q != null && !q.isBlank()) {
                String pattern = "%" + q.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("description")), pattern),
                        cb.like(cb.lower(root.get("brand")), pattern),
                        cb.like(cb.lower(root.get("category")), pattern)
                ));
            }

            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("category")), category.trim().toLowerCase()));
            }

            if (brand != null && !brand.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("brand")), brand.trim().toLowerCase()));
            }

            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), minPrice));
            }

            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), maxPrice));
            }

            if (featured != null) {
                predicates.add(cb.equal(root.get("featured"), featured));
            }

            ProductStatus effectiveStatus = status != null ? status : ProductStatus.ACTIVE;
            predicates.add(cb.equal(root.get("status"), effectiveStatus));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
