package backend.repositories.specifications;

import backend.models.core.Company;
import backend.models.core.InventoryAdjustment;
import backend.models.core.Product;
import backend.models.core.ProductReview;
import backend.models.enums.AdjustmentReason;
import backend.models.enums.CompanyStatus;
import backend.models.enums.ProductStatus;
import backend.models.enums.ReviewStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpecificationsTest {

    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID USER_ID    = UUID.randomUUID();

    private CriteriaBuilder cb()   { return mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS); }
    @SuppressWarnings("unchecked")
    private <T> Root<T> root()     { return mock(Root.class, RETURNS_DEEP_STUBS); }
    @SuppressWarnings("unchecked")
    private <T> CriteriaQuery<T> query() { return mock(CriteriaQuery.class, RETURNS_DEEP_STUBS); }

    // ── CompanySpecification ──────────────────────────────────────────────────

    @Test
    void companySpec_allNull_doesNotThrow() {
        var cb = cb(); var root = this.<Company>root(); var query = this.<Object>query();
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));
        Specification<Company> spec = CompanySpecification.withFilters(null, null, null, null);
        assertDoesNotThrow(() -> spec.toPredicate(root, query, cb));
    }

    @Test
    void companySpec_withQ_exercisesSearchBranch() {
        var cb = cb(); var root = this.<Company>root(); var query = this.<Object>query();
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));
        Specification<Company> spec = CompanySpecification.withFilters("tech", null, null, null);
        assertDoesNotThrow(() -> spec.toPredicate(root, query, cb));
    }

    @Test
    void companySpec_withIndustry_exercisesIndustryBranch() {
        var cb = cb(); var root = this.<Company>root(); var query = this.<Object>query();
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));
        Specification<Company> spec = CompanySpecification.withFilters(null, "software", null, null);
        assertDoesNotThrow(() -> spec.toPredicate(root, query, cb));
    }

    @Test
    void companySpec_withCountry_exercisesCountryBranch() {
        var cb = cb(); var root = this.<Company>root(); var query = this.<Object>query();
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));
        Specification<Company> spec = CompanySpecification.withFilters(null, null, "CA", null);
        assertDoesNotThrow(() -> spec.toPredicate(root, query, cb));
    }

    @Test
    void companySpec_withStatus_exercisesStatusBranch() {
        var cb = cb(); var root = this.<Company>root(); var query = this.<Object>query();
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));
        Specification<Company> spec = CompanySpecification.withFilters(null, null, null, CompanyStatus.ACTIVE);
        assertDoesNotThrow(() -> spec.toPredicate(root, query, cb));
    }

    @Test
    void companySpec_blankQ_skipsSearchBranch() {
        var cb = cb(); var root = this.<Company>root(); var query = this.<Object>query();
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));
        Specification<Company> spec = CompanySpecification.withFilters("   ", "  ", "  ", null);
        assertDoesNotThrow(() -> spec.toPredicate(root, query, cb));
    }

    // ── AdjustmentSpecification ───────────────────────────────────────────────

    @Test
    void adjustmentSpec_allNull_doesNotThrow() {
        var cb = cb(); var root = this.<InventoryAdjustment>root(); var query = this.<Object>query();
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));
        Specification<InventoryAdjustment> spec = AdjustmentSpecification.withFilters(COMPANY_ID, null, null, null, null, null);
        assertDoesNotThrow(() -> spec.toPredicate(root, query, cb));
    }

    @Test
    void adjustmentSpec_allFilters_exercisesAllBranches() {
        var cb = cb(); var root = this.<InventoryAdjustment>root(); var query = this.<Object>query();
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));
        Specification<InventoryAdjustment> spec = AdjustmentSpecification.withFilters(
                COMPANY_ID, AdjustmentReason.DAMAGE, Instant.now().minusSeconds(3600), Instant.now(),
                PRODUCT_ID, USER_ID);
        assertDoesNotThrow(() -> spec.toPredicate(root, query, cb));
    }

    // ── InventorySpecification ────────────────────────────────────────────────

    @Test
    void inventorySpec_allNull_doesNotThrow() {
        var cb = cb(); var root = this.<Product>root(); var query = this.<Object>query();
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));
        Specification<Product> spec = InventorySpecification.withFilters(COMPANY_ID, null, null, null, null, null, null, null);
        assertDoesNotThrow(() -> spec.toPredicate(root, query, cb));
    }

    @Test
    void inventorySpec_outOfStock_exercisesSwitch() {
        var cb = cb(); var root = this.<Product>root(); var query = this.<Object>query();
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));
        Specification<Product> spec = InventorySpecification.withFilters(COMPANY_ID, "OUT_OF_STOCK", "widget", null, null, ProductStatus.ACTIVE, null, null);
        assertDoesNotThrow(() -> spec.toPredicate(root, query, cb));
    }

    @Test
    void inventorySpec_inStock_exercisesSwitch() {
        var cb = cb(); var root = this.<Product>root(); var query = this.<Object>query();
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));
        Specification<Product> spec = InventorySpecification.withFilters(COMPANY_ID, "IN_STOCK", null, "electronics", "Apple", null, null, null);
        assertDoesNotThrow(() -> spec.toPredicate(root, query, cb));
    }

    @Test
    void inventorySpec_lowStock_exercisesSwitch() {
        var cb = cb(); var root = this.<Product>root(); var query = this.<Object>query();
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));
        Specification<Product> spec = InventorySpecification.withFilters(COMPANY_ID, "LOW_STOCK", null, null, null, null, null, null);
        assertDoesNotThrow(() -> spec.toPredicate(root, query, cb));
    }

    @Test
    void inventorySpec_untracked_exercisesSwitch() {
        var cb = cb(); var root = this.<Product>root(); var query = this.<Object>query();
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));
        Specification<Product> spec = InventorySpecification.withFilters(COMPANY_ID, "UNTRACKED", null, null, null, null, null, null);
        assertDoesNotThrow(() -> spec.toPredicate(root, query, cb));
    }

    @Test
    void inventorySpec_withMinMaxStock_exercisesBothRanges() {
        var cb = cb(); var root = this.<Product>root(); var query = this.<Object>query();
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));
        Specification<Product> spec = InventorySpecification.withFilters(COMPANY_ID, null, null, null, null, null, 5, 100);
        assertDoesNotThrow(() -> spec.toPredicate(root, query, cb));
    }

    @Test
    void inventorySpec_withCursor_exercisesCursorBranch() {
        var cb = cb(); var root = this.<Product>root(); var query = this.<Object>query();
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));
        Specification<Product> spec = InventorySpecification.withFilters(COMPANY_ID, null, null, null, null, null, null, null,
                Instant.now(), UUID.randomUUID());
        assertDoesNotThrow(() -> spec.toPredicate(root, query, cb));
    }

    // ── ProductSpecification ──────────────────────────────────────────────────

    @Test
    void productSpec_allNull_doesNotThrow() {
        var cb = cb(); var root = this.<Product>root(); var query = this.<Object>query();
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));
        Specification<Product> spec = ProductSpecification.withFilters(COMPANY_ID, null, null, null, null, null, null, null, null, null, null);
        assertDoesNotThrow(() -> spec.toPredicate(root, query, cb));
    }

    @Test
    void productSpec_withQAndFilters_exercisesTextBranches() {
        var cb = cb(); var root = this.<Product>root(); var query = this.<Object>query();
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));
        Specification<Product> spec = ProductSpecification.withFilters(
                COMPANY_ID, "shirt", "clothing", "Nike",
                new BigDecimal("10.00"), new BigDecimal("100.00"),
                true, ProductStatus.ACTIVE, true, null, null);
        assertDoesNotThrow(() -> spec.toPredicate(root, query, cb));
    }

    @Test
    void productSpec_withDiscountCategory_exercisesSubqueryBranch() {
        var cb = cb(); var root = this.<Product>root(); var query = this.<Object>query();
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));
        Specification<Product> spec = ProductSpecification.withFilters(
                COMPANY_ID, null, null, null, null, null, null, null, null, "seasonal", null);
        assertDoesNotThrow(() -> spec.toPredicate(root, query, cb));
    }

    @Test
    void productSpec_withHasDiscountTrue_exercisesDiscountSubquery() {
        var cb = cb(); var root = this.<Product>root(); var query = this.<Object>query();
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));
        Specification<Product> spec = ProductSpecification.withFilters(
                COMPANY_ID, null, null, null, null, null, null, null, null, null, true);
        assertDoesNotThrow(() -> spec.toPredicate(root, query, cb));
    }

    // ── ReviewSpecification ───────────────────────────────────────────────────

    @Test
    void reviewSpec_allNull_doesNotThrow() {
        var cb = cb(); var root = this.<ProductReview>root(); var query = this.<Object>query();
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));
        Specification<ProductReview> spec = ReviewSpecification.withFilters(PRODUCT_ID, null, null, null);
        assertDoesNotThrow(() -> spec.toPredicate(root, query, cb));
    }

    @Test
    void reviewSpec_withRatings_exercisesInBranch() {
        var cb = cb(); var root = this.<ProductReview>root(); var query = this.<Object>query();
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));
        Specification<ProductReview> spec = ReviewSpecification.withFilters(PRODUCT_ID, List.of(4, 5), null, null);
        assertDoesNotThrow(() -> spec.toPredicate(root, query, cb));
    }

    @Test
    void reviewSpec_verifiedOnly_exercisesVerifiedBranch() {
        var cb = cb(); var root = this.<ProductReview>root(); var query = this.<Object>query();
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));
        Specification<ProductReview> spec = ReviewSpecification.withFilters(PRODUCT_ID, null, true, null);
        assertDoesNotThrow(() -> spec.toPredicate(root, query, cb));
    }

    @Test
    void reviewSpec_hasMedia_exercisesSubqueryBranch() {
        var cb = cb(); var root = this.<ProductReview>root(); var query = this.<Object>query();
        when(cb.and(any(Predicate[].class))).thenReturn(mock(Predicate.class));
        Specification<ProductReview> spec = ReviewSpecification.withFilters(PRODUCT_ID, null, null, true);
        Predicate result = spec.toPredicate(root, query, cb);
        assertNotNull(result);
    }
}
