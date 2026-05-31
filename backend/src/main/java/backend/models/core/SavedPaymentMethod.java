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

/**
 * A customer's reusable Stripe PaymentMethod. Created when a SetupIntent succeeds
 * so subscriptions can be charged on future cycles without a fresh checkout.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "saved_payment_methods", indexes = {
        @Index(name = "idx_spm_user", columnList = "user_id"),
        @Index(name = "idx_spm_stripe_id", columnList = "stripe_payment_method_id", unique = true)
})
@EntityListeners(AuditingEntityListener.class)
public class SavedPaymentMethod {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "stripe_payment_method_id", nullable = false, length = 100, unique = true)
    private String stripePaymentMethodId;

    @Column(name = "stripe_customer_id", nullable = false, length = 100)
    private String stripeCustomerId;

    @Column(nullable = true, length = 30)
    private String brand;

    @Column(nullable = true, length = 4)
    private String last4;

    @Column(nullable = true)
    private Integer expMonth;

    @Column(nullable = true)
    private Integer expYear;

    @Column(nullable = false)
    private boolean isDefault = false;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
