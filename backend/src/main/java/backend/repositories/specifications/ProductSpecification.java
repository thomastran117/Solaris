package backend.repositories.specifications;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import backend.models.core.Product;
import backend.models.core.PromotionRule;
import backend.models.enums.DiscountStatus;
import backend.models.enums.ProductStatus;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
     * @param featured          when non-null, filters by featured flag
     * @param status            defaults to ACTIVE when null
     * @param discountCategory  when non-null, restricts to products linked to a discount with this category
     * @param hasDiscount       when true, restricts to products with at least one active discount;
     *                          when false, restricts to products with no active discount (JPA path: skip false)
     */
    public static Specification<Product> withFilters(
            UUID companyId,
            String q,
            String category,
            String brand,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Boolean featured,
            ProductStatus status,
            Boolean listed,
            String discountCategory,
            Boolean hasDiscount) {

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

            if (listed != null) {
                predicates.add(cb.equal(root.get("listed"), listed));
            }

            if (discountCategory != null && !discountCategory.isBlank()) {
                String cat = discountCategory.trim().toLowerCase();
                // Either the product appears in a rule's targetProducts whose description
                // matches the category, or a company-wide rule (empty targetProducts) with
                // matching description exists — in which case every company product passes.
                Subquery<Long> byTarget = query.subquery(Long.class);
                Root<PromotionRule> rt = byTarget.from(PromotionRule.class);
                byTarget.select(rt.join("targetProducts").get("id"))
                        .where(cb.equal(cb.lower(rt.get("description")), cat));

                Subquery<Long> companyWide = query.subquery(Long.class);
                Root<PromotionRule> rw = companyWide.from(PromotionRule.class);
                companyWide.select(rw.get("id")).where(
                        cb.equal(rw.get("company").get("id"), companyId),
                        cb.equal(cb.lower(rw.get("description")), cat),
                        cb.isEmpty(rw.get("targetProducts"))
                );
                predicates.add(cb.or(root.get("id").in(byTarget), cb.exists(companyWide)));
            }

            if (Boolean.TRUE.equals(hasDiscount)) {
                Subquery<Long> byTarget = query.subquery(Long.class);
                Root<PromotionRule> rt = byTarget.from(PromotionRule.class);
                byTarget.select(rt.join("targetProducts").get("id"))
                        .where(
                                cb.equal(rt.get("company").get("id"), companyId),
                                cb.equal(rt.get("status"), DiscountStatus.ACTIVE),
                                cb.or(cb.isNull(rt.get("startDate")),
                                      cb.lessThanOrEqualTo(rt.get("startDate"), cb.currentTimestamp())),
                                cb.or(cb.isNull(rt.get("endDate")),
                                      cb.greaterThan(rt.get("endDate"), cb.currentTimestamp()))
                        );

                Subquery<Long> companyWide = query.subquery(Long.class);
                Root<PromotionRule> rw = companyWide.from(PromotionRule.class);
                companyWide.select(rw.get("id")).where(
                        cb.equal(rw.get("company").get("id"), companyId),
                        cb.equal(rw.get("status"), DiscountStatus.ACTIVE),
                        cb.or(cb.isNull(rw.get("startDate")),
                              cb.lessThanOrEqualTo(rw.get("startDate"), cb.currentTimestamp())),
                        cb.or(cb.isNull(rw.get("endDate")),
                              cb.greaterThan(rw.get("endDate"), cb.currentTimestamp())),
                        cb.isEmpty(rw.get("targetProducts"))
                );
                predicates.add(cb.or(root.get("id").in(byTarget), cb.exists(companyWide)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
