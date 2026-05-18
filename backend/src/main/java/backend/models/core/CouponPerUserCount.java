package backend.models.core;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tracks per-user redemption counts for coupons that have a maxUsesPerUser cap.
 * The unique constraint on (coupon_id, user_id) enables an atomic ON DUPLICATE KEY UPDATE
 * increment, so concurrent orders by the same user cannot both pass the per-user limit check.
 */
@Entity
@Table(name = "coupon_per_user_counts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_coupon_per_user",
                columnNames = {"coupon_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
public class CouponPerUserCount {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private java.util.UUID id;

    @Column(name = "coupon_id", nullable = false)
    private long couponId;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(nullable = false)
    private int count;
}
