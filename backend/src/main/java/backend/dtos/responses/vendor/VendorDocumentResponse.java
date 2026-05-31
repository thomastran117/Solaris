package backend.dtos.responses.vendor;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class VendorDocumentResponse {
    private UUID id;
    private UUID marketplaceVendorId;
    private String documentType;
    private String s3Key;
    private Instant uploadedAt;
    private Instant verifiedAt;
    private String rejectionNote;
}
