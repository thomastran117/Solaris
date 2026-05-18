package backend.models.core;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "coupon_redemptions", indexes = {
        @Index(name = "idx_redemption_coupon", columnList = "coupon_id"),
        @Index(name = "idx_redemption_user",   columnList = "user_id"),
        @Index(name = "idx_redemption_ip_time", columnList = "ip, redeemed_at")
})
@Getter
@Setter
@NoArgsConstructor
public class CouponRedemption {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;

    /** One coupon redemption per order — enforced at the DB and application layer. */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** The actual amount saved — snapshotted at redemption time. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Column(nullable = false)
    private Instant redeemedAt;

    /** Client IP at redemption time. Used by CouponAbuseEvaluator for cross-account IP velocity. */
    @Column(nullable = true, length = 45)
    private String ip;
}
