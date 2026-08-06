package backend.models.core;

import backend.models.enums.FeedbackCategory;
import backend.models.enums.FeedbackStatus;
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
@Table(
        name = "platform_feedback",
        indexes = {
                @Index(name = "idx_feedback_user",     columnList = "user_id"),
                @Index(name = "idx_feedback_status",   columnList = "status"),
                @Index(name = "idx_feedback_category", columnList = "category"),
                @Index(name = "idx_feedback_created",  columnList = "created_at")
        }
)
@EntityListeners(AuditingEntityListener.class)
public class PlatformFeedback {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User submittedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FeedbackCategory category = FeedbackCategory.OTHER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FeedbackStatus status = FeedbackStatus.OPEN;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(nullable = true)
    private Integer rating;

    @Column(name = "page_context", nullable = true, length = 500)
    private String pageContext;

    @Column(name = "reviewed_at", nullable = true)
    private Instant reviewedAt;

    @Column(name = "reviewed_by_id", nullable = true)
    private UUID reviewedById;

    @Version
    @Column(nullable = false)
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
