package backend.dtos.requests.company;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateCompanyRequest {

    @Size(max = 255, message = "Company name must not exceed 255 characters")
    private String name;

    @Size(max = 255, message = "Address must not exceed 255 characters")
    private String address;

    @Size(max = 100, message = "City must not exceed 100 characters")
    private String city;

    @Size(max = 100, message = "Country must not exceed 100 characters")
    private String country;

    @Size(max = 20, message = "Postal code must not exceed 20 characters")
    private String postalCode;

    @Pattern(regexp = "^\\+?[0-9\\s\\-().]{7,30}$", message = "Invalid phone number format")
    private String phoneNumber;

    @Size(max = 500, message = "Logo URL must not exceed 500 characters")
    private String logoUrl;

    @Email(message = "Invalid email format")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    @Size(max = 255, message = "Website must not exceed 255 characters")
    private String website;

    private String description;

    @Size(max = 100, message = "Industry must not exceed 100 characters")
    private String industry;

    @Size(max = 100, message = "Registration number must not exceed 100 characters")
    private String registrationNumber;

    @Size(max = 100, message = "Tax ID must not exceed 100 characters")
    private String taxId;

    @Min(value = 1800, message = "Founded year must be 1800 or later")
    @Max(value = 9999, message = "Founded year is invalid")
    private Integer foundedYear;

    @Min(value = 1, message = "Employee count must be at least 1")
    private Integer employeeCount;
}
