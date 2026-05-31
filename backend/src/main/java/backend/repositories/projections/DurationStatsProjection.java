package backend.repositories.projections;

/**
 * Aggregate row returned by SLA duration queries (fulfillment, refund resolution, pick delay).
 * Average is in seconds; downstream service converts to hours for display.
 */
public interface DurationStatsProjection {
    Long getCount();
    Double getAvgSeconds();
}
