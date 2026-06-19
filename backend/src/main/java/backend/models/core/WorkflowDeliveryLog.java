package backend.models.core;

import backend.models.enums.WorkflowActionType;
import backend.models.enums.WorkflowDeliveryStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "workflow_delivery_logs", indexes = {
        @Index(name = "idx_wdlog_enrollment", columnList = "enrollment_id")
})
public class WorkflowDeliveryLog {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Version
    private int version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(nullable = false, name = "enrollment_id", columnDefinition = "BINARY(16)")
    private UUID enrollmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkflowActionType channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkflowDeliveryStatus status;

    @Column(nullable = false, name = "sent_at")
    private Instant sentAt;

    public WorkflowDeliveryLog(UUID enrollmentId, WorkflowActionType channel, WorkflowDeliveryStatus status, Instant sentAt) {
        this.enrollmentId = enrollmentId;
        this.channel = channel;
        this.status = status;
        this.sentAt = sentAt;
    }
}
