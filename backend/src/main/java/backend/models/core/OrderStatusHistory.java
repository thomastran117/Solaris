package backend.models.core;

import backend.models.enums.OrderHistoryEventType;
import backend.models.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "order_status_history", indexes = {
        @Index(name = "idx_osh_order_id", columnList = "order_id")
})
public class OrderStatusHistory {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private OrderHistoryEventType eventType;

    /** Current order status at time of event. Null for non-STATUS_CHANGED events. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = true, length = 25)
    private OrderStatus status;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /** Null for system-generated events (webhooks, carrier callbacks). */
    @Column(name = "actor_id", nullable = true)
    private UUID actorId;

    @Column(nullable = true, length = 500)
    private String note;
}
