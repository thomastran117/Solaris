package backend.models.core;

import backend.models.enums.WorkflowActionType;
import backend.models.enums.WorkflowStatus;
import backend.models.enums.WorkflowTrigger;
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
@Table(name = "marketing_workflows", indexes = {
        @Index(name = "idx_mwf_company", columnList = "company_id")
})
@EntityListeners(AuditingEntityListener.class)
public class MarketingWorkflow {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false, name = "company_id", columnDefinition = "BINARY(16)")
    private UUID companyId;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private WorkflowTrigger trigger;

    @Column(nullable = false, name = "delay_hours")
    private int delayHours = 0;

    @Column(nullable = true, name = "target_segment_id", columnDefinition = "BINARY(16)")
    private UUID targetSegmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "action_type", length = 20)
    private WorkflowActionType actionType;

    @Column(nullable = true, name = "email_subject", length = 255)
    private String emailSubject;

    @Column(nullable = true, name = "email_body", columnDefinition = "TEXT")
    private String emailBody;

    @Column(nullable = false, name = "cooldown_days")
    private int cooldownDays = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkflowStatus status = WorkflowStatus.ACTIVE;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
