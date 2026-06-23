package backend.dtos.responses.tax;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class TaxRateResponse {
    private UUID id;
    private String country;
    private String state;
    private String postalCode;
    private BigDecimal rate;
    private boolean shippingTaxable;
    private boolean active;
    private String description;
    private Instant createdAt;
    private Instant updatedAt;
}
