package backend.repositories.specifications;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import backend.models.core.Product;
import backend.models.enums.ProductStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class InventorySpecification {

    private InventorySpecification() {}

    /**
     * Builds a dynamic specification for inventory list/search queries.
     * Delegates to the cursor-aware overload with null cursor values (first page).
     */
    public static Specification<Product> withFilters(
            UUID companyId,
            String stockStatus,
            String q,
            String category,
            String brand,
            ProductStatus status,
            Integer minStock,
            Integer maxStock) {

        return withFilters(companyId, stockStatus, q, category, brand, status, minStock, maxStock, null, null);
    }

    /**
     * Cursor-aware overload. When {@code cursorUpdatedAt} and {@code cursorId} are non-null,
     * appends a keyset predicate for {@code (updatedAt DESC, id DESC)} pagination:
     * {@code (updatedAt < cursorUpdatedAt) OR (updatedAt = cursorUpdatedAt AND id < cursorId)}.
     */
    public static Specification<Product> withFilters(
            UUID companyId,
            String stockStatus,
            String q,
            String category,
            String brand,
            ProductStatus status,
            Integer minStock,
            Integer maxStock,
            Instant cursorUpdatedAt,
            UUID cursorId) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("company").get("id"), companyId));

            if (q != null && !q.isBlank()) {
                String pattern = "%" + q.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("sku")), pattern)
                ));
            }

            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("category")), category.trim().toLowerCase()));
            }

            if (brand != null && !brand.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("brand")), brand.trim().toLowerCase()));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (stockStatus != null) {
                switch (stockStatus.toUpperCase()) {
                    case "OUT_OF_STOCK" -> predicates.add(cb.and(
                            cb.isNotNull(root.get("stock")),
                            cb.equal(root.get("stock"), 0)));
                    case "IN_STOCK" -> predicates.add(cb.and(
                            cb.isNotNull(root.get("stock")),
                            cb.greaterThan(root.get("stock"), 0)));
                    case "LOW_STOCK" -> predicates.add(cb.and(
                            cb.isNotNull(root.get("stock")),
                            cb.isNotNull(root.get("lowStockThreshold")),
                            cb.greaterThan(root.get("stock"), 0),
                            cb.lessThanOrEqualTo(root.get("stock"), root.get("lowStockThreshold"))));
                    case "UNTRACKED" -> predicates.add(cb.isNull(root.get("stock")));
                    // "ALL" and unknown values: no additional predicate
                }
            }

            if (minStock != null) {
                predicates.add(cb.and(
                        cb.isNotNull(root.get("stock")),
                        cb.greaterThanOrEqualTo(root.get("stock"), minStock)));
            }

            if (maxStock != null) {
                predicates.add(cb.and(
                        cb.isNotNull(root.get("stock")),
                        cb.lessThanOrEqualTo(root.get("stock"), maxStock)));
            }

            if (cursorUpdatedAt != null && cursorId != null) {
                Predicate beforeTime = cb.lessThan(root.get("updatedAt"), cursorUpdatedAt);
                Predicate sameTimeEarlierId = cb.and(
                        cb.equal(root.get("updatedAt"), cursorUpdatedAt),
                        cb.lessThan(root.get("id"), cursorId)
                );
                predicates.add(cb.or(beforeTime, sameTimeEarlierId));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
