package backend.models.core;

import backend.models.enums.WorkflowEnrollmentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "workflow_enrollments", indexes = {
        @Index(name = "idx_wenroll_workflow_user", columnList = "workflow_id, user_id"),
        @Index(name = "idx_wenroll_status_fire", columnList = "status, fire_at")
})
@EntityListeners(AuditingEntityListener.class)
public class WorkflowEnrollment {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false, name = "workflow_id", columnDefinition = "BINARY(16)")
    private UUID workflowId;

    @Column(nullable = false, name = "user_id", columnDefinition = "BINARY(16)")
    private UUID userId;

    @Column(nullable = false, name = "enrolled_at")
    private Instant enrolledAt;

    @Column(nullable = false, name = "fire_at")
    private Instant fireAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkflowEnrollmentStatus status = WorkflowEnrollmentStatus.SCHEDULED;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
