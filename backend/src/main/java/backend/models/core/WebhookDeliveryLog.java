package backend.models.core;

import backend.models.enums.WebhookDeliveryStatus;
import backend.models.enums.WebhookEventType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@Table(
        name = "webhook_delivery_logs",
        indexes = {
                @Index(name = "idx_whdl_sub_created", columnList = "subscription_id, created_at")
        }
)
public class WebhookDeliveryLog {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private CompanyWebhookSubscription subscription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private WebhookEventType eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column
    private Integer responseStatus;

    @Column(nullable = false)
    private int attemptCount;

    @Column
    private Instant deliveredAt;

    @Column
    private Instant nextRetryAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WebhookDeliveryStatus status = WebhookDeliveryStatus.PENDING;

    @Version
    private Long version;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
