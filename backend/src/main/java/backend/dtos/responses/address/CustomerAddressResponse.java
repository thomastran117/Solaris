package backend.dtos.responses.address;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class CustomerAddressResponse {
    private UUID id;
    private UUID userId;
    private String label;
    private String recipientName;
    private String street;
    private String street2;
    private String city;
    private String state;
    private String postalCode;
    private String country;
    private String phoneNumber;
    private boolean isDefault;
    private Instant createdAt;
    private Instant updatedAt;
}
