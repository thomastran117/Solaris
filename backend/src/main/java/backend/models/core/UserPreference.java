package backend.models.core;

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
@Table(name = "user_preference")
@EntityListeners(AuditingEntityListener.class)
public class UserPreference {

    @Id
    @Column(name = "user_id", columnDefinition = "BINARY(16)")
    private UUID userId;

    @Column(nullable = false)
    private boolean trackingOptOut = false;

    @Column(nullable = false)
    private boolean pushEnabled = false;

    @Column(nullable = false)
    private boolean smsEnabled = false;

    @Column(length = 30)
    private String smsPhoneNumber;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    public UserPreference(UUID userId) {
        this.userId = userId;
    }
}
