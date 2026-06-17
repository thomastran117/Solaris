package backend.dtos.responses.product;

import java.util.List;

/**
 * Unified comparison matrix for 2–4 products in a marketplace. {@code products} are the columns
 * (in request order); {@code attributes} are the rows — one per distinct attribute key across all
 * compared products, aligned via {@link ComparisonRow#valuesByProductId()}.
 */
public record ProductComparisonResponse(
        List<ComparedProduct> products,
        List<ComparisonRow> attributes
) {}
