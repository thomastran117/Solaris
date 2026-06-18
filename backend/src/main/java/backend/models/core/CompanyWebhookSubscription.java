package backend.models.core;

import backend.models.enums.WebhookEventType;
import backend.models.enums.WebhookSubscriptionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Table(
        name = "company_webhook_subscriptions",
        indexes = {
                @Index(name = "idx_wh_sub_company", columnList = "company_id")
        }
)
public class CompanyWebhookSubscription {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 2048)
    private String url;

    // Length 256 accommodates AES-256-GCM encrypted secrets (Base64 IV+ciphertext ≈ 112 chars)
    // as well as plaintext hex secrets (64 chars) for environments without an encryption key.
    @Column(name = "secret_token", nullable = false, length = 256)
    private String secretToken;

    @Column(name = "verification_challenge", nullable = false, length = 64)
    private String verificationChallenge;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "webhook_subscription_events",
            joinColumns = @JoinColumn(name = "subscription_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private Set<WebhookEventType> events = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WebhookSubscriptionStatus status = WebhookSubscriptionStatus.PENDING_VERIFICATION;

    @Version
    private Long version;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
