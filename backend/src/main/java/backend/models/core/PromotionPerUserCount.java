package backend.models.core;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per-user redemption counter for PromotionRule.maxUsesPerUser enforcement.
 * Unique constraint on (rule_id, user_id) enables atomic ON DUPLICATE KEY UPDATE increments,
 * mirroring CouponPerUserCount.
 */
@Entity
@Table(name = "promotion_per_user_counts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_promo_per_user",
                columnNames = {"rule_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
public class PromotionPerUserCount {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private java.util.UUID id;

    @Column(name = "rule_id", nullable = false, columnDefinition = "BINARY(16)")
    private java.util.UUID ruleId;

    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    private java.util.UUID userId;

    @Column(nullable = false)
    private int count;
}
