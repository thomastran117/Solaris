package backend.dtos.responses.inventory;

import backend.models.enums.LocationType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class LocationResponse {
    private UUID id;
    private UUID companyId;
    private String name;
    private String code;
    private String address;
    private String city;
    private String country;
    private boolean active;
    private int displayOrder;
    private Double latitude;
    private Double longitude;
    private BigDecimal fulfillmentCost;
    private LocationType type;
    private int handlingDays;
    private Integer pickupReadyHours;
    private Instant createdAt;
    private Instant updatedAt;
}
