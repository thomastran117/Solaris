package backend.services.impl.pricing;

import java.util.UUID;
import backend.dtos.requests.marketplace.CreateCommissionPolicyRequest;
import backend.dtos.responses.marketplace.CommissionPolicyResponse;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.CommissionPolicy;
import backend.models.core.CommissionRule;
import backend.models.enums.CommissionRuleType;
import backend.repositories.CommissionPolicyRepository;
import backend.repositories.MarketplaceProfileRepository;
import backend.services.intf.pricing.CommissionPolicyService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CommissionPolicyServiceImpl implements CommissionPolicyService {

    private final CommissionPolicyRepository policyRepository;
    private final MarketplaceProfileRepository marketplaceProfileRepository;

    public CommissionPolicyServiceImpl(
            CommissionPolicyRepository policyRepository,
            MarketplaceProfileRepository marketplaceProfileRepository) {
        this.policyRepository = policyRepository;
        this.marketplaceProfileRepository = marketplaceProfileRepository;
    }

    @Override
    @Transactional
    public CommissionPolicyResponse createPolicy(UUID marketplaceId, UUID operatorUserId, CreateCommissionPolicyRequest request) {
        assertOperator(marketplaceId, operatorUserId);

        CommissionPolicy policy = new CommissionPolicy();
        policy.setMarketplaceId(marketplaceId);
        policy.setName(request.getName());
        policy.setDefaultRate(request.getDefaultRate());
        policy.setEffectiveFrom(request.getEffectiveFrom());
        policy.setEffectiveTo(request.getEffectiveTo());
        policy.setActive(request.isActive());

        if (request.getRules() != null) {
            for (CreateCommissionPolicyRequest.RuleRequest rr : request.getRules()) {
                CommissionRule rule = new CommissionRule();
                rule.setPolicy(policy);
                rule.setRuleType(CommissionRuleType.valueOf(rr.getRuleType().toUpperCase()));
                rule.setMatchValue(rr.getMatchValue());
                rule.setRate(rr.getRate());
                rule.setPriority(rr.getPriority());
                policy.getRules().add(rule);
            }
        }

        return toResponse(policyRepository.save(policy));
    }

    @Override
    @Transactional
    public void deletePolicy(UUID policyId, UUID marketplaceId, UUID operatorUserId) {
        assertOperator(marketplaceId, operatorUserId);
        CommissionPolicy policy = policyRepository.findById(policyId)
                .filter(p -> p.getMarketplaceId().equals(marketplaceId))
                .orElseThrow(() -> new ResourceNotFoundException("Commission policy not found"));
        policyRepository.delete(policy);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommissionPolicyResponse> listPolicies(UUID marketplaceId, UUID operatorUserId) {
        assertOperator(marketplaceId, operatorUserId);
        return policyRepository.findByMarketplaceIdAndActiveTrue(marketplaceId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private void assertOperator(UUID marketplaceId, UUID userId) {
        marketplaceProfileRepository.findByCompanyId(marketplaceId)
                .filter(p -> p.getCompany().getOwner().getId().equals(userId))
                .orElseThrow(() -> new ForbiddenException("You are not an operator of this marketplace"));
    }

    private CommissionPolicyResponse toResponse(CommissionPolicy p) {
        List<CommissionPolicyResponse.RuleResponse> rules = p.getRules().stream()
                .map(r -> new CommissionPolicyResponse.RuleResponse(
                        r.getId(), r.getRuleType().name(), r.getMatchValue(), r.getRate(), r.getPriority()))
                .toList();
        return new CommissionPolicyResponse(
                p.getId(), p.getMarketplaceId(), p.getName(), p.getDefaultRate(),
                p.getEffectiveFrom(), p.getEffectiveTo(), p.isActive(), rules,
                p.getCreatedAt(), p.getUpdatedAt());
    }
}
