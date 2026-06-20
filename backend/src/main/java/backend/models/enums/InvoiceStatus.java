package backend.models.enums;

/**
 * Lifecycle of a B2B net-terms invoice (Feature 12).
 *
 * <ul>
 *   <li>{@code ISSUED} — invoice generated, payment outstanding.</li>
 *   <li>{@code PAID} — vendor recorded payment received.</li>
 *   <li>{@code OVERDUE} — past the due date and still unpaid.</li>
 *   <li>{@code CANCELLED} — voided (e.g. order cancelled).</li>
 * </ul>
 */
public enum InvoiceStatus {
    ISSUED,
    PAID,
    OVERDUE,
    CANCELLED
}
