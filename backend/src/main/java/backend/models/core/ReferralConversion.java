package backend.models.core;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "referral_conversions", indexes = {
        @Index(name = "idx_referral_conv_referrer", columnList = "referrer_account_id"),
        @Index(name = "idx_referral_conv_referred", columnList = "referred_account_id"),
        @Index(name = "idx_referral_conv_company", columnList = "company_id")
})
@EntityListeners(AuditingEntityListener.class)
public class ReferralConversion {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(nullable = false, name = "referrer_account_id")
    private UUID referrerAccountId;

    @Column(nullable = false, name = "referred_account_id")
    private UUID referredAccountId;

    @Column(nullable = false, name = "company_id")
    private UUID companyId;

    /** The order that triggered the referral conversion. */
    @Column(nullable = true, name = "order_id")
    private UUID orderId;

    /** Points that were awarded to the referrer. */
    @Column(nullable = false, name = "points_awarded")
    private int pointsAwarded;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
