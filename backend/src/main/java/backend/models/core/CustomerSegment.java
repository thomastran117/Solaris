package backend.models.core;

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

/**
 * Platform-wide customer tier (e.g. VIP, WHOLESALE). Users are assigned to segments
 * manually by platform admins. Segments are referenced by PromotionRule.targetSegments
 * to gate which users see a given rule.
 */
@Entity
@Table(name = "customer_segments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_segment_code", columnNames = "code")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class CustomerSegment {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    /** Stored uppercase. E.g. "VIP", "WHOLESALE". Immutable after create. */
    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = true, length = 500)
    private String description;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;
}
