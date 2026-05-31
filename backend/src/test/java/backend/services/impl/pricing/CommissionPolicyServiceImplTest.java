package backend.services.impl.pricing;

import backend.dtos.requests.marketplace.CreateCommissionPolicyRequest;
import backend.dtos.responses.marketplace.CommissionPolicyResponse;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.CommissionPolicy;
import backend.models.core.CommissionRule;
import backend.models.core.Company;
import backend.models.core.MarketplaceProfile;
import backend.models.core.User;
import backend.models.enums.CommissionRuleType;
import backend.models.enums.UserRole;
import backend.repositories.CommissionPolicyRepository;
import backend.repositories.MarketplaceProfileRepository;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommissionPolicyServiceImplTest {

    private static final UUID USER_ID = TestIds.uuid(1);
    private static final UUID MARKETPLACE_ID = TestIds.uuid(2);
    private static final UUID POLICY_ID = TestIds.uuid(3);

    private CommissionPolicyRepository policyRepository;
    private MarketplaceProfileRepository marketplaceProfileRepository;
    private CommissionPolicyServiceImpl service;

    @BeforeEach
    void setUp() {
        policyRepository = mock(CommissionPolicyRepository.class);
        marketplaceProfileRepository = mock(MarketplaceProfileRepository.class);
        service = new CommissionPolicyServiceImpl(policyRepository, marketplaceProfileRepository);
    }

    @Test
    void createPolicy_mapsRulesAndReturnsResponse() {
        when(marketplaceProfileRepository.findByCompanyId(MARKETPLACE_ID))
                .thenReturn(Optional.of(profile(USER_ID)));
        when(policyRepository.save(any(CommissionPolicy.class))).thenAnswer(inv -> {
            CommissionPolicy policy = inv.getArgument(0);
            policy.setId(POLICY_ID);
            policy.setCreatedAt(Instant.parse("2026-05-19T00:00:00Z"));
            policy.setUpdatedAt(Instant.parse("2026-05-19T00:00:00Z"));
            policy.getRules().forEach(rule -> rule.setId(TestIds.uuid(10)));
            return policy;
        });

        CreateCommissionPolicyRequest request = new CreateCommissionPolicyRequest();
        request.setName("Default Policy");
        request.setDefaultRate(new BigDecimal("0.1500"));
        request.setActive(true);

        CreateCommissionPolicyRequest.RuleRequest rule = new CreateCommissionPolicyRequest.RuleRequest();
        rule.setRuleType("category");
        rule.setMatchValue("Office");
        rule.setRate(new BigDecimal("0.1000"));
        rule.setPriority(5);
        request.setRules(List.of(rule));

        CommissionPolicyResponse response = service.createPolicy(MARKETPLACE_ID, USER_ID, request);

        assertEquals(POLICY_ID, response.getId());
        assertEquals("Default Policy", response.getName());
        assertEquals("CATEGORY", response.getRules().get(0).getRuleType());
        assertEquals("Office", response.getRules().get(0).getMatchValue());
        verify(policyRepository).save(argThat(policy ->
                policy.getMarketplaceId().equals(MARKETPLACE_ID)
                        && policy.getRules().size() == 1
                        && policy.getRules().get(0).getPolicy() == policy
                        && policy.getRules().get(0).getRuleType() == CommissionRuleType.CATEGORY
        ));
    }

    @Test
    void listPolicies_returnsOnlyActivePolicies() {
        when(marketplaceProfileRepository.findByCompanyId(MARKETPLACE_ID))
                .thenReturn(Optional.of(profile(USER_ID)));
        when(policyRepository.findByMarketplaceIdAndActiveTrue(MARKETPLACE_ID))
                .thenReturn(List.of(policy()));

        List<CommissionPolicyResponse> responses = service.listPolicies(MARKETPLACE_ID, USER_ID);

        assertEquals(1, responses.size());
        assertEquals(POLICY_ID, responses.get(0).getId());
    }

    @Test
    void deletePolicy_removesMarketplaceScopedPolicy() {
        when(marketplaceProfileRepository.findByCompanyId(MARKETPLACE_ID))
                .thenReturn(Optional.of(profile(USER_ID)));
        when(policyRepository.findById(POLICY_ID)).thenReturn(Optional.of(policy()));

        service.deletePolicy(POLICY_ID, MARKETPLACE_ID, USER_ID);

        verify(policyRepository).delete(any(CommissionPolicy.class));
    }

    @Test
    void deletePolicy_missingPolicyThrowsNotFound() {
        when(marketplaceProfileRepository.findByCompanyId(MARKETPLACE_ID))
                .thenReturn(Optional.of(profile(USER_ID)));
        when(policyRepository.findById(POLICY_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.deletePolicy(POLICY_ID, MARKETPLACE_ID, USER_ID));
    }

    @Test
    void deletePolicy_wrongMarketplaceThrowsNotFound() {
        when(marketplaceProfileRepository.findByCompanyId(MARKETPLACE_ID))
                .thenReturn(Optional.of(profile(USER_ID)));
        CommissionPolicy otherMarketplacePolicy = policy();
        otherMarketplacePolicy.setMarketplaceId(TestIds.uuid(99));
        when(policyRepository.findById(POLICY_ID)).thenReturn(Optional.of(otherMarketplacePolicy));

        assertThrows(ResourceNotFoundException.class,
                () -> service.deletePolicy(POLICY_ID, MARKETPLACE_ID, USER_ID));
    }

    @Test
    void createPolicy_nonOperatorThrowsForbidden() {
        when(marketplaceProfileRepository.findByCompanyId(MARKETPLACE_ID)).thenReturn(Optional.empty());

        CreateCommissionPolicyRequest request = new CreateCommissionPolicyRequest();
        request.setName("x");
        request.setDefaultRate(new BigDecimal("0.1"));

        assertThrows(ForbiddenException.class,
                () -> service.createPolicy(MARKETPLACE_ID, USER_ID, request));
    }

    private MarketplaceProfile profile(UUID ownerId) {
        User owner = new User();
        owner.setId(ownerId);
        owner.setRole(UserRole.MERCHANT);

        Company company = new Company();
        company.setId(MARKETPLACE_ID);
        company.setOwner(owner);
        company.setName("Marketplace");

        MarketplaceProfile profile = new MarketplaceProfile();
        profile.setCompany(company);
        return profile;
    }

    private CommissionPolicy policy() {
        CommissionPolicy policy = new CommissionPolicy();
        policy.setId(POLICY_ID);
        policy.setMarketplaceId(MARKETPLACE_ID);
        policy.setName("Default Policy");
        policy.setDefaultRate(new BigDecimal("0.1500"));
        policy.setActive(true);

        CommissionRule rule = new CommissionRule();
        rule.setId(TestIds.uuid(10));
        rule.setPolicy(policy);
        rule.setRuleType(CommissionRuleType.CATEGORY);
        rule.setMatchValue("Office");
        rule.setRate(new BigDecimal("0.1000"));
        rule.setPriority(5);

        policy.setRules(List.of(rule));
        policy.setCreatedAt(Instant.parse("2026-05-19T00:00:00Z"));
        policy.setUpdatedAt(Instant.parse("2026-05-19T00:00:00Z"));
        return policy;
    }
}
