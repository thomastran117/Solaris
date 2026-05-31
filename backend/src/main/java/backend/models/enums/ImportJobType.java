package backend.models.enums;

public enum ImportJobType {
    /** CSV with full product fields — creates or updates products by SKU. */
    PRODUCT_UPSERT,
    /** CSV with SKU + stock columns only — generates inventory adjustments. */
    INVENTORY_SYNC,
    /** Export of the company's catalogue to a CSV file. */
    EXPORT
}
