package backend.dtos.responses.company;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Public, anonymous-safe projection of a company. Exposes only fields a casual visitor
 * (or a competitor) could already discover from the storefront. PII and operational
 * data — owner id, contact email/phone, tax id, registration number, employee count —
 * stay on {@link CompanyResponse} and are only returned via authenticated endpoints.
 */
@Getter
@AllArgsConstructor
public class PublicCompanyResponse {
    private Long id;
    private String name;
    private String city;
    private String country;
    private String logoUrl;
    private String website;
    private String description;
    private String industry;
    private Integer foundedYear;
}
