package backend.services.pricing.config;

/** Where a {@code PERCENTAGE_OFF} / {@code FIXED_OFF} rule applies. */
public enum PromotionScope {
    /** Applied per matching line — evaluator iterates lines and discounts each. */
    LINE,
    /** Applied once against the whole cart subtotal. */
    ORDER
}
