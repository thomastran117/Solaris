package backend.models.core;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "abandoned_cart_recovery", uniqueConstraints = {
        @UniqueConstraint(name = "uk_abandoned_recovery_user_date",
                          columnNames = {"user_id", "sent_date"})
})
@EntityListeners(AuditingEntityListener.class)
public class AbandonedCartRecovery {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "sent_date", nullable = false)
    private LocalDate sentDate;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
