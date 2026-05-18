package backend.dtos.requests.vendor;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class ApplyVendorRequest {

    @NotNull(message = "Vendor company ID is required")
    private UUID vendorCompanyId;
}
