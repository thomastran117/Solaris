package backend.services.intf.customers;

import java.util.UUID;
import backend.dtos.requests.segment.CreateCustomerSegmentRequest;
import backend.dtos.requests.segment.UpdateCustomerSegmentRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.segment.CustomerSegmentResponse;

public interface CustomerSegmentService {
    PagedResponse<CustomerSegmentResponse> listSegments(int page, int size);
    CustomerSegmentResponse getSegment(UUID segmentId);
    CustomerSegmentResponse createSegment(CreateCustomerSegmentRequest request);
    CustomerSegmentResponse updateSegment(UUID segmentId, UpdateCustomerSegmentRequest request);
    void deleteSegment(UUID segmentId);

    /** Tag a user with a segment. No-op if already tagged. */
    void assignSegmentToUser(UUID userId, UUID segmentId);

    /** Untag a user from a segment. No-op if not tagged. */
    void removeSegmentFromUser(UUID userId, UUID segmentId);
}
