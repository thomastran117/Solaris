package backend.models.core;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "company_follows",
        uniqueConstraints = @UniqueConstraint(name = "uq_cf_user_company", columnNames = {"user_id", "company_id"}),
        indexes = {
                @Index(name = "idx_cf_company_notif", columnList = "company_id, notifications_enabled"),
                @Index(name = "idx_cf_user",          columnList = "user_id")
        }
)
public class CompanyFollow {

    @Id
    @org.hibernate.annotations.UuidGenerator(style = org.hibernate.annotations.UuidGenerator.Style.TIME)
    @Column(columnDefinition = "BINARY(16)")
    private java.util.UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "followed_at", nullable = false, updatable = false)
    private Instant followedAt;

    @Column(name = "notifications_enabled", nullable = false)
    private boolean notificationsEnabled = true;
}
