package backend.services.intf.promotions;

import java.util.UUID;
import backend.dtos.requests.pricing.CreatePromotionRuleRequest;
import backend.dtos.requests.pricing.UpdatePromotionRuleRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.pricing.PromotionRuleResponse;

public interface PromotionRuleService {
    PagedResponse<PromotionRuleResponse> listRules(UUID companyId, UUID ownerId, int page, int size);
    PromotionRuleResponse getRule(UUID companyId, UUID ruleId, UUID ownerId);
    PromotionRuleResponse createRule(UUID companyId, UUID ownerId, CreatePromotionRuleRequest request);
    PromotionRuleResponse updateRule(UUID companyId, UUID ruleId, UUID ownerId, UpdatePromotionRuleRequest request);
    void deleteRule(UUID companyId, UUID ruleId, UUID ownerId);
}
