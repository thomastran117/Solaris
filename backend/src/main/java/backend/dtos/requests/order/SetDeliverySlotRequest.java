package backend.dtos.requests.order;

import backend.models.enums.DeliveryWindow;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Customer request to set or update the preferred delivery slot on an order.
 * The allowed date range (tomorrow … today + 14 days) is validated in the service
 * layer where "today" is resolved server-side.
 */
@Getter
@Setter
public class SetDeliverySlotRequest {

    @NotNull(message = "Preferred delivery date is required")
    private LocalDate preferredDeliveryDate;

    /** Optional preferred time window. Null leaves the window unspecified. */
    private DeliveryWindow preferredDeliveryWindow;
}
