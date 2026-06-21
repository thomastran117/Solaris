package backend.dtos.requests.b2b;

import jakarta.validation.constraints.Size;

/** Records an out-of-band payment against a net-terms invoice. */
public record MarkInvoicePaidRequest(
        @Size(max = 255) String paymentReference
) {}
