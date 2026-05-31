package backend.services.intf.pricing;

import java.util.UUID;
import backend.dtos.requests.marketplace.CreateCommissionPolicyRequest;
import backend.dtos.responses.marketplace.CommissionPolicyResponse;

import java.util.List;

public interface CommissionPolicyService {

    CommissionPolicyResponse createPolicy(UUID marketplaceId, UUID operatorUserId, CreateCommissionPolicyRequest request);

    void deletePolicy(UUID policyId, UUID marketplaceId, UUID operatorUserId);

    List<CommissionPolicyResponse> listPolicies(UUID marketplaceId, UUID operatorUserId);
}
