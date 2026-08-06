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
        name = "review_media",
        indexes = @Index(name = "idx_media_review", columnList = "review_id, position")
)
@EntityListeners(AuditingEntityListener.class)
public class ReviewMedia {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    private java.util.UUID id;

    @Column(name = "review_id", nullable = false)
    private UUID reviewId;

    @Column(nullable = false, length = 1024)
    private String url;

    @Column(nullable = false)
    private int position;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
