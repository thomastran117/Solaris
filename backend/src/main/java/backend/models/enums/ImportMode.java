package backend.models.enums;

public enum ImportMode {
    /** Rows must match an existing SKU; new SKUs are rejected. */
    UPDATE_ONLY,
    /** Rows must NOT match an existing SKU; duplicates are rejected. */
    CREATE_ONLY,
    /** Default — existing SKUs update, new SKUs create. */
    UPSERT
}
