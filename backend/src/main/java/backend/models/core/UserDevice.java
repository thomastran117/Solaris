package backend.models.core;

import backend.http.DeviceType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
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
    name = "user_devices",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_user_device_fingerprint",
        columnNames = {"user_id", "fingerprint"}
    ),
    indexes = @Index(name = "idx_user_device_user", columnList = "user_id")
)
@EntityListeners(AuditingEntityListener.class)
public class UserDevice {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 64)
    private String fingerprint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private DeviceType deviceType;

    @Column(nullable = false, length = 100)
    private String browser;

    @Column(nullable = false, length = 100)
    private String os;

    @Column(nullable = true, length = 512)
    private String userAgent;

    @Column(nullable = false, length = 45)
    private String lastIp;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant lastSeenAt;

    @Column(length = 512)
    private String fcmToken;

    @Column(length = 512)
    private String apnsToken;

    private Instant tokenUpdatedAt;
}
