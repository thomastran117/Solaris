package backend.repositories.projections;

/** Ship-hours stats row used for SLA evaluation and analytics summaries. */
public interface VendorShipHoursProjection {
    Double getAvgShipHours();
    Long getTotalShipped();
    Long getTotalLate();
}
