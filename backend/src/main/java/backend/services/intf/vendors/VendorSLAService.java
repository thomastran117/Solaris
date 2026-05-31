package backend.services.intf.vendors;

import java.util.UUID;
import backend.dtos.requests.sla.CreateSLAPolicyRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.sla.VendorSLABreachResponse;
import backend.dtos.responses.sla.VendorSLAMetricResponse;
import backend.dtos.responses.sla.VendorSLAPolicyResponse;

import java.util.List;

public interface VendorSLAService {

    // Policy management (operator)
    VendorSLAPolicyResponse createPolicy(UUID marketplaceId, UUID operatorUserId, CreateSLAPolicyRequest request);
    VendorSLAPolicyResponse getActivePolicy(UUID marketplaceId);
    List<VendorSLAPolicyResponse> listPolicies(UUID marketplaceId);

    // Metrics (vendor self-service + operator)
    PagedResponse<VendorSLAMetricResponse> listMetrics(UUID marketplaceId, UUID vendorId, UUID actorUserId, int page, int size);
    VendorSLAMetricResponse getLatestMetric(UUID marketplaceId, UUID vendorId, UUID actorUserId);

    // Breaches (vendor self-service + operator)
    PagedResponse<VendorSLABreachResponse> listBreaches(UUID marketplaceId, UUID vendorId, UUID actorUserId, int page, int size);
    VendorSLABreachResponse resolveBreach(UUID breachId, UUID operatorUserId, UUID marketplaceId);
}
