package backend.services.intf;

import backend.dtos.requests.inventory.AdjustStockRequest;
import backend.dtos.requests.inventory.BulkAdjustRequest;
import backend.dtos.requests.inventory.CreateLocationRequest;
import backend.dtos.requests.inventory.CreateRestockRequest;
import backend.dtos.requests.inventory.UpdateLocationRequest;
import backend.dtos.requests.inventory.UpdateRestockRequest;
import backend.dtos.requests.product.AddProductImageRequest;
import backend.dtos.requests.product.BatchCreateProductsRequest;
import backend.dtos.requests.product.CreateProductOptionRequest;
import backend.dtos.requests.product.CreateProductRequest;
import backend.dtos.requests.product.CreateProductVariantRequest;
import backend.dtos.requests.product.SetProductAttributesRequest;
import backend.dtos.requests.product.UpdateProductOptionRequest;
import backend.dtos.requests.product.UpdateProductRequest;
import backend.dtos.requests.product.UpdateProductVariantRequest;

/**
 * Centralized input sanitization for product-family request DTOs.
 *
 * <p>Split of responsibilities with Jakarta Bean Validation annotations:
 * <ul>
 *   <li>{@code @SafeText}/{@code @SafeRichText}/{@code @SafeIdentifier} reject
 *       unsafe input (HTML, control chars, profanity, disallowed chars) via
 *       {@link jakarta.validation.ConstraintValidator}s that delegate to the
 *       {@code isSafe*} predicates on this service. Rejections surface as
 *       HTTP 400 through the global exception handler.
 *   <li>The {@code normalize(...)} overloads apply benign mutations (trim,
 *       collapse whitespace, uppercase currency, lowercase category) after
 *       validation has passed. Controllers call these before delegating to
 *       the service layer.
 * </ul>
 */
public interface SanitizationService {

    // ---- Primitives (used by both validators and normalize overloads) ----

    /** Null-safe trim + collapse internal runs of whitespace to a single space. */
    String normalizeText(String value);

    /**
     * Trim only; preserves newlines and internal formatting.
     * Use for rich-text / multi-line fields (description, notes, address).
     */
    String normalizeRichText(String value);

    /** Null-safe trim + upper-case. Use for currency, location code. */
    String normalizeCode(String value);

    /** Null-safe trim + lower-case. Use for category. */
    String normalizeCategory(String value);

    /** True if null/blank or a short plain-text value with no HTML, control chars, or profanity. */
    boolean isSafePlainText(String value);

    /**
     * True if null/blank or rich text whose HTML is within the allowed whitelist
     * (b, i, u, p, br, ul, ol, li, strong, em) and contains no profanity.
     */
    boolean isSafeRichText(String value);

    /** True if null/blank or matches {@code [A-Za-z0-9_\-.]+} and contains no profanity. */
    boolean isSafeIdentifier(String value);

    /** Case-insensitive word-boundary profanity check. Null/blank → false. */
    boolean containsProfanity(String value);

    // ---- DTO normalization overloads (called from controllers after @Valid) ----

    void normalize(CreateProductRequest request);
    void normalize(UpdateProductRequest request);
    void normalize(BatchCreateProductsRequest request);
    void normalize(CreateProductVariantRequest request);
    void normalize(UpdateProductVariantRequest request);
    void normalize(CreateProductOptionRequest request);
    void normalize(UpdateProductOptionRequest request);
    void normalize(AddProductImageRequest request);
    void normalize(SetProductAttributesRequest request);

    void normalize(CreateRestockRequest request);
    void normalize(UpdateRestockRequest request);
    void normalize(AdjustStockRequest request);
    void normalize(BulkAdjustRequest request);

    void normalize(CreateLocationRequest request);
    void normalize(UpdateLocationRequest request);
}
