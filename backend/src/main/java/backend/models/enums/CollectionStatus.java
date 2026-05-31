package backend.models.enums;

/**
 * Public visibility of a Collection. Only ACTIVE collections appear on the marketplace
 * storefront / vendor storefront. DRAFT lets merchants stage a collection before launch;
 * ARCHIVED hides it without deletion (keeps history of pinned/boosted assignments).
 */
public enum CollectionStatus {
    DRAFT,
    ACTIVE,
    ARCHIVED
}
