package backend.repositories.projections;

/**
 * Aggregate row for the supplier lateness metric. {@code late} counts restock
 * requests received past their {@code expectedArrivalDate}; {@code avgLateDays}
 * is the average lateness across the late ones (in days, may be null when
 * {@code late=0}).
 */
public interface SupplierLatenessProjection {
    Long getTotal();
    Long getLate();
    Double getAvgLateDays();
}
