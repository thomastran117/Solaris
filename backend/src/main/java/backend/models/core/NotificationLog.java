package backend.models.core;

import backend.models.enums.NotificationChannel;
import backend.models.enums.NotificationDeliveryStatus;
import jakarta.persistence.*;
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
@Table(
    name = "notification_log",
    indexes = {
        @Index(name = "idx_notification_log_user", columnList = "user_id"),
        @Index(name = "idx_notification_log_sent_at", columnList = "sent_at")
    }
)
@EntityListeners(AuditingEntityListener.class)
public class NotificationLog {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NotificationChannel channel;

    @Column(nullable = false, length = 60)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NotificationDeliveryStatus status;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(length = 255)
    private String externalId;

    @Column(length = 1000)
    private String errorMessage;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public NotificationLog(UUID userId, NotificationChannel channel, String eventType,
                           NotificationDeliveryStatus status, Instant sentAt,
                           String externalId, String errorMessage) {
        this.userId = userId;
        this.channel = channel;
        this.eventType = eventType;
        this.status = status;
        this.sentAt = sentAt;
        this.externalId = externalId;
        this.errorMessage = errorMessage;
    }
}
