package backend.models.core;

import backend.models.enums.WorkflowActionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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

    @Column(nullable = false, name = "enrollment_id", columnDefinition = "BINARY(16)")
    private UUID enrollmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkflowActionType channel;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false, name = "sent_at")
    private Instant sentAt;

    public WorkflowDeliveryLog(UUID enrollmentId, WorkflowActionType channel, String status, Instant sentAt) {
        this.enrollmentId = enrollmentId;
        this.channel = channel;
        this.status = status;
        this.sentAt = sentAt;
    }
}
