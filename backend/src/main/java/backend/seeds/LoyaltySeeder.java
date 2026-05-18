package backend.seeds;

import java.util.UUID;
import backend.models.core.LoyaltyAccount;
import backend.repositories.LoyaltyAccountRepository;
import backend.seeds.CompanySeeder.SeededCompanies;
import backend.seeds.UserSeeder.SeededUsers;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class LoyaltySeeder {

    private final LoyaltyAccountRepository loyaltyAccountRepository;

    public void seed(SeededUsers users, SeededCompanies companies) {
        account(users.alice().getId(),  companies.tech().getId(),     2450L, 5800L);
        account(users.bob().getId(),    companies.style().getId(),     150L,  150L);
        account(users.carol().getId(),  companies.wellness().getId(),  890L,  1200L);
    }

    private void account(UUID userId, Long companyId, long points, long lifetime) {
        if (loyaltyAccountRepository.findByUserIdAndCompanyId(userId, companyId).isPresent()) return;
        LoyaltyAccount la = new LoyaltyAccount();
        la.setUserId(userId);
        la.setCompanyId(companyId);
        la.setPointsBalance(points);
        la.setLifetimePoints(lifetime);
        loyaltyAccountRepository.save(la);
    }
}
