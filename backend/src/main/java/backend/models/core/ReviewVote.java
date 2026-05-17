package backend.models.core;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "review_votes",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_vote_review_user",
                columnNames = {"review_id", "user_id"}
        ),
        indexes = {
                @Index(name = "idx_vote_review", columnList = "review_id"),
                @Index(name = "idx_vote_user", columnList = "user_id")
        }
)
@EntityListeners(AuditingEntityListener.class)
public class ReviewVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "review_id", nullable = false)
    private Long reviewId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
