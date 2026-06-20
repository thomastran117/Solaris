package backend.dtos.responses.inventory;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class SupplierResponse {
    private UUID id;
    private UUID companyId;
    private String name;
    private String email;
    private String phone;
    private String address;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;
}
