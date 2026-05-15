package backend.models.enums;

public enum LoyaltyTransactionType {
    EARN_ORDER,
    EARN_BONUS,
    EARN_BIRTHDAY,
    REDEEM_ORDER,
    RESTORE_ORDER,
    /**
     * Reversal of a previously-recorded {@link #EARN_ORDER} when the source order is
     * refunded. {@code pointsDelta} is negative and proportional to the refund amount
     * vs the order total. Multiple reversals can exist per order (incremental refunds).
     */
    EARN_REVERSAL,
    CONVERT_TO_CREDIT,
    EXPIRE,
    ADJUST
}
