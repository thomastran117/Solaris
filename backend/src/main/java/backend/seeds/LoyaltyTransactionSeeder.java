package backend.seeds;

import backend.models.core.LoyaltyAccount;
import backend.models.core.LoyaltyTransaction;
import backend.models.enums.LoyaltyTransactionType;
import backend.repositories.LoyaltyAccountRepository;
import backend.repositories.LoyaltyTransactionRepository;
import backend.seeds.CompanySeeder.SeededCompanies;
import backend.seeds.OrderSeeder.SeededOrders;
import backend.seeds.UserSeeder.SeededUsers;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class LoyaltyTransactionSeeder {

    private final LoyaltyAccountRepository accountRepository;
    private final LoyaltyTransactionRepository transactionRepository;

    public void seed(SeededUsers users, SeededCompanies companies, SeededOrders orders) {
        seedAlice(users, companies, orders);
        seedBob(users, companies, orders);
        seedCarol(users, companies, orders);
    }

    private void seedAlice(SeededUsers users, SeededCompanies companies, SeededOrders orders) {
        LoyaltyAccount account = accountRepository
                .findByUserIdAndCompanyId(users.alice().getId(), companies.tech().getId())
                .orElse(null);
        if (account == null) return;
        if (!transactionRepository.findByUserIdAndCompanyId(users.alice().getId(), companies.tech().getId()).isEmpty()) return;

        // Earn from alice1 (TechGadgets, DELIVERED)
        transaction(account, LoyaltyTransactionType.EARN_ORDER, 490, 0,
                orders.alice1().getId(), "Points earned — order delivered");
        // Earn from alice4 (TechGadgets, PAID)
        transaction(account, LoyaltyTransactionType.EARN_ORDER, 980, 0,
                orders.alice4().getId(), "Points earned — order confirmed");
        // Earn bonus
        transaction(account, LoyaltyTransactionType.EARN_BONUS, 500, 0,
                null, "Welcome bonus — first order with TechGadgets");
        // Redemption
        transaction(account, LoyaltyTransactionType.REDEEM_ORDER, -200, -200,
                orders.alice4().getId(), "Points redeemed for $2.00 discount at checkout");
    }

    private void seedBob(SeededUsers users, SeededCompanies companies, SeededOrders orders) {
        LoyaltyAccount account = accountRepository
                .findByUserIdAndCompanyId(users.bob().getId(), companies.style().getId())
                .orElse(null);
        if (account == null) return;
        if (!transactionRepository.findByUserIdAndCompanyId(users.bob().getId(), companies.style().getId()).isEmpty()) return;

        // Earn from bob1 (StyleHub, DELIVERED)
        transaction(account, LoyaltyTransactionType.EARN_ORDER, 150, 0,
                orders.bob1().getId(), "Points earned — order delivered");
    }

    private void seedCarol(SeededUsers users, SeededCompanies companies, SeededOrders orders) {
        LoyaltyAccount account = accountRepository
                .findByUserIdAndCompanyId(users.carol().getId(), companies.wellness().getId())
                .orElse(null);
        if (account == null) return;
        if (!transactionRepository.findByUserIdAndCompanyId(users.carol().getId(), companies.wellness().getId()).isEmpty()) return;

        // Earn from carol1 (WellnessWorld, DELIVERED)
        transaction(account, LoyaltyTransactionType.EARN_ORDER, 420, 0,
                orders.carol1().getId(), "Points earned — order delivered");
        // Birthday bonus
        transaction(account, LoyaltyTransactionType.EARN_BIRTHDAY, 250, 0,
                null, "Birthday bonus — September reward");
        // Additional earn bonus
        transaction(account, LoyaltyTransactionType.EARN_ORDER, 220, 0,
                orders.carol1().getId(), "Points earned — bonus for repeat purchase");
    }

    private void transaction(LoyaltyAccount account, LoyaltyTransactionType type,
                              long pointsDelta, long valueCents, UUID sourceOrderId, String reason) {
        LoyaltyTransaction tx = new LoyaltyTransaction();
        tx.setAccount(account);
        tx.setUserId(account.getUserId());
        tx.setCompanyId(account.getCompanyId());
        tx.setType(type);
        tx.setPointsDelta(pointsDelta);
        tx.setValueCents(valueCents);
        tx.setSourceOrderId(sourceOrderId);
        tx.setReason(reason);
        transactionRepository.save(tx);
    }
}
