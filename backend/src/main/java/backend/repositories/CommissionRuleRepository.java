package backend.repositories;

import backend.models.core.CommissionRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommissionRuleRepository extends JpaRepository<CommissionRule, java.util.UUID> {

    List<CommissionRule> findByPolicyIdOrderByPriorityDesc(java.util.UUID policyId);

    void deleteByPolicyId(java.util.UUID policyId);
}
