package backend.dtos.responses.company;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class CompanyResponse {
    private UUID id;
    private UUID ownerId;
    private String name;
    private String address;
    private String city;
    private String country;
    private String postalCode;
    private String phoneNumber;
    private String logoUrl;
    private String email;
    private String website;
    private String description;
    private String industry;
    private String registrationNumber;
    private String taxId;
    private Integer foundedYear;
    private Integer employeeCount;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
}
