package backend.models.enums;

/**
 * Payment terms for a B2B quote/order (Feature 12).
 *
 * <ul>
 *   <li>{@code IMMEDIATE} — charged via Stripe at order creation, same as retail.</li>
 *   <li>{@code NET_30} / {@code NET_60} — fulfilled on credit; a {@code B2BInvoice} is issued
 *       with a due date 30/60 days out and no Stripe capture at order time.</li>
 * </ul>
 */
public enum PaymentTerms {
    IMMEDIATE,
    NET_30,
    NET_60;

    /** Days until the invoice is due for net terms; 0 for IMMEDIATE (no invoice). */
    public int netDays() {
        return switch (this) {
            case IMMEDIATE -> 0;
            case NET_30 -> 30;
            case NET_60 -> 60;
        };
    }

    public boolean isNet() {
        return this != IMMEDIATE;
    }
}
