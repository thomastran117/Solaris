package backend.models.core;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "product_answer_upvotes",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_upvote_answer_user",
                columnNames = {"answer_id", "user_id"}
        ),
        indexes = {
                @Index(name = "idx_upvote_answer", columnList = "answer_id"),
                @Index(name = "idx_upvote_user", columnList = "user_id")
        }
)
@EntityListeners(AuditingEntityListener.class)
public class ProductAnswerUpvote {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "answer_id", nullable = false)
    private UUID answerId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
