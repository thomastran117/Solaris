package backend.dtos.requests.inventory;

import backend.annotations.safeIdentifier.SafeIdentifier;
import backend.annotations.safeRichText.SafeRichText;
import backend.annotations.safeText.SafeText;
import backend.models.enums.LocationType;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CreateLocationRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name must be at most 255 characters")
    @SafeText
    private String name;

    @NotBlank(message = "Code is required")
    @Size(max = 100, message = "Code must be at most 100 characters")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$",
             message = "Code may only contain letters, digits, hyphens, and underscores")
    @SafeIdentifier
    private String code;

    @Size(max = 500, message = "Address must be at most 500 characters")
    @SafeRichText
    private String address;

    @Size(max = 100, message = "City must be at most 100 characters")
    @SafeText
    private String city;

    @Size(max = 100, message = "State/province must be at most 100 characters")
    @SafeText
    private String stateProvince;

    @Size(max = 20, message = "Postal code must be at most 20 characters")
    @SafeText
    private String postalCode;

    @Size(max = 100, message = "Country must be at most 100 characters")
    @SafeText
    private String country;

    private Integer displayOrder;

    @DecimalMin(value = "-90.0",  message = "Latitude must be >= -90")
    @DecimalMax(value = "90.0",   message = "Latitude must be <= 90")
    private Double latitude;

    @DecimalMin(value = "-180.0", message = "Longitude must be >= -180")
    @DecimalMax(value = "180.0",  message = "Longitude must be <= 180")
    private Double longitude;

    @DecimalMin(value = "0.0", message = "Fulfillment cost cannot be negative")
    private BigDecimal fulfillmentCost;

    private LocationType type;

    @Min(value = 0, message = "Handling days cannot be negative")
    @Max(value = 30, message = "Handling days must be at most 30")
    private Integer handlingDays;

    @Min(value = 0, message = "Pickup ready hours cannot be negative")
    @Max(value = 168, message = "Pickup ready hours must be at most 168 (one week)")
    private Integer pickupReadyHours;
}
