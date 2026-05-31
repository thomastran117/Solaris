package backend.seeds;

import backend.models.core.CommissionPolicy;
import backend.models.core.CommissionRule;
import backend.models.core.Company;
import backend.models.core.MarketplaceProfile;
import backend.models.core.MarketplaceVendor;
import backend.models.enums.CommissionRuleType;
import backend.models.enums.OnboardingStep;
import backend.models.enums.PayoutSchedule;
import backend.models.enums.StripeConnectStatus;
import backend.models.enums.VendorStatus;
import backend.models.enums.VendorTier;
import backend.repositories.CommissionPolicyRepository;
import backend.repositories.MarketplaceProfileRepository;
import backend.repositories.MarketplaceVendorRepository;
import backend.seeds.CompanySeeder.SeededCompanies;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class MarketplaceSeeder {

    private final MarketplaceProfileRepository profileRepository;
    private final MarketplaceVendorRepository vendorRepository;
    private final CommissionPolicyRepository policyRepository;

    public record SeededMarketplace(MarketplaceProfile profile, CommissionPolicy policy) {}

    public SeededMarketplace seed(SeededCompanies companies) {
        MarketplaceProfile profile = seedProfile(companies.tech());
        CommissionPolicy policy = seedPolicy(profile);
        seedVendor(companies.tech(), companies.style(),    "acct_test_stylehub",    profile);
        seedVendor(companies.tech(), companies.wellness(), "acct_test_wellness",    profile);
        seedVendor(companies.tech(), companies.home(),     "acct_test_homenest",    profile);
        seedVendor(companies.tech(), companies.sport(),    "acct_test_sportzone",   profile);
        return new SeededMarketplace(profile, policy);
    }

    private MarketplaceProfile seedProfile(Company tech) {
        return profileRepository.findByCompanyId(tech.getId()).orElseGet(() -> {
            MarketplaceProfile p = new MarketplaceProfile();
            p.setCompany(tech);
            p.setSlug("techgadgets");
            p.setPayoutSchedule(PayoutSchedule.WEEKLY);
            p.setHoldPeriodDays(7);
            p.setDefaultCurrency("USD");
            p.setAcceptingApplications(true);
            return profileRepository.save(p);
        });
    }

    private CommissionPolicy seedPolicy(MarketplaceProfile profile) {
        var existing = policyRepository.findByMarketplaceIdAndActiveTrue(profile.getCompany().getId());
        if (!existing.isEmpty()) {
            // update profile default if not set
            if (profile.getDefaultCommissionPolicyId() == null) {
                profile.setDefaultCommissionPolicyId(existing.get(0).getId());
                profileRepository.save(profile);
            }
            return existing.get(0);
        }

        CommissionPolicy policy = new CommissionPolicy();
        policy.setMarketplaceId(profile.getCompany().getId());
        policy.setName("Standard Commission Policy");
        policy.setDefaultRate(new BigDecimal("0.1500"));
        policy.setEffectiveFrom(Instant.now());
        policy.setActive(true);

        CommissionRule electronics = rule(policy, CommissionRuleType.CATEGORY, "Electronics & Technology", "0.1200", 10);
        CommissionRule apparel     = rule(policy, CommissionRuleType.CATEGORY, "Fashion & Apparel",        "0.1800", 9);
        CommissionRule wellness    = rule(policy, CommissionRuleType.CATEGORY, "Health & Wellness",        "0.1400", 8);

        policy.getRules().add(electronics);
        policy.getRules().add(apparel);
        policy.getRules().add(wellness);

        CommissionPolicy saved = policyRepository.save(policy);
        profile.setDefaultCommissionPolicyId(saved.getId());
        profileRepository.save(profile);
        return saved;
    }

    private CommissionRule rule(CommissionPolicy policy, CommissionRuleType type, String matchValue, String rate, int priority) {
        CommissionRule r = new CommissionRule();
        r.setPolicy(policy);
        r.setRuleType(type);
        r.setMatchValue(matchValue);
        r.setRate(new BigDecimal(rate));
        r.setPriority(priority);
        return r;
    }

    private void seedVendor(Company marketplace, Company vendor, String stripeAccountId, MarketplaceProfile profile) {
        if (vendorRepository.existsByMarketplaceIdAndVendorCompanyId(marketplace.getId(), vendor.getId())) return;
        MarketplaceVendor mv = new MarketplaceVendor();
        mv.setMarketplace(marketplace);
        mv.setVendorCompany(vendor);
        mv.setStatus(VendorStatus.APPROVED);
        mv.setTier(VendorTier.STANDARD);
        mv.setOnboardingStep(OnboardingStep.COMPLETE);
        mv.setStripeConnectAccountId(stripeAccountId);
        mv.setStripeConnectStatus(StripeConnectStatus.ENABLED);
        mv.setChargesEnabled(true);
        mv.setPayoutsEnabled(true);
        mv.setAppliedAt(Instant.now().minusSeconds(60 * 60 * 24 * 30));
        mv.setApprovedAt(Instant.now().minusSeconds(60 * 60 * 24 * 20));
        vendorRepository.save(mv);
    }
}
