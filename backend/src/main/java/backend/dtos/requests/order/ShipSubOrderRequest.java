package backend.dtos.requests.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ShipSubOrderRequest {

    @NotBlank(message = "Tracking number is required")
    @Size(max = 100, message = "Tracking number must not exceed 100 characters")
    private String trackingNumber;

    @NotBlank(message = "Carrier is required")
    @Size(max = 60, message = "Carrier must not exceed 60 characters")
    private String carrier;

    @Size(max = 500, message = "Fulfillment note must not exceed 500 characters")
    private String fulfillmentNote;
}
