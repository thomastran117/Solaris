package backend.dtos.responses.inventory;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@AllArgsConstructor
public class RestockRequestResponse {
    private UUID id;
    private UUID companyId;
    private UUID productId;
    private String productName;
    private UUID variantId;
    private String variantSku;
    private UUID locationId;
    private String locationName;
    private int requestedQty;
    private Integer receivedQty;
    private LocalDate expectedArrivalDate;
    private String status;
    private String supplierNote;
    private UUID createdByUserId;
    private Instant createdAt;
    private Instant updatedAt;
}
