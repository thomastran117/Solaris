package backend.models.core;

import backend.models.enums.QAStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
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
@AllArgsConstructor
@Table(
        name = "product_answers",
        indexes = {
                @Index(name = "idx_answer_question", columnList = "question_id"),
                @Index(name = "idx_answer_user", columnList = "answerer_id")
        }
)
@EntityListeners(AuditingEntityListener.class)
public class ProductAnswer {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private ProductQuestion question;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "answerer_id", nullable = false)
    private User answeredBy;

    @Column(name = "answer_text", nullable = false, columnDefinition = "TEXT")
    private String answerText;

    @Column(name = "is_vendor_answer", nullable = false)
    private boolean isVendorAnswer = false;

    @Column(name = "upvote_count", nullable = false)
    private int upvoteCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QAStatus status = QAStatus.VISIBLE;

    @Column(name = "report_count", nullable = false)
    private int reportCount = 0;

    @Version
    @Column(nullable = false)
    private Long version;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
