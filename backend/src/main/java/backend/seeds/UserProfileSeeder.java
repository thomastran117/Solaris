package backend.seeds;

import backend.http.DeviceType;
import backend.models.core.CustomerCredit;
import backend.models.core.SavedPaymentMethod;
import backend.models.core.User;
import backend.models.core.UserDevice;
import backend.models.core.UserPreference;
import backend.models.enums.CreditEntryType;
import backend.repositories.CustomerCreditRepository;
import backend.repositories.SavedPaymentMethodRepository;
import backend.repositories.UserDeviceRepository;
import backend.repositories.UserPreferenceRepository;
import backend.seeds.UserSeeder.SeededUsers;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class UserProfileSeeder {

    private final UserDeviceRepository deviceRepository;
    private final UserPreferenceRepository preferenceRepository;
    private final SavedPaymentMethodRepository paymentMethodRepository;
    private final CustomerCreditRepository creditRepository;

    public void seed(SeededUsers users) {
        seedPreferences(users);
        seedDevices(users);
        seedPaymentMethods(users);
        seedCredits(users);
    }

    private void seedPreferences(SeededUsers users) {
        for (User u : new User[]{
                users.admin(), users.techMerchant(), users.styleMerchant(),
                users.wellnessMerchant(), users.homeMerchant(), users.sportMerchant(),
                users.alice(), users.bob(), users.carol()}) {
            if (preferenceRepository.existsById(u.getId())) continue;
            UserPreference pref = new UserPreference(u.getId());
            pref.setTrackingOptOut(u == users.carol());
            preferenceRepository.save(pref);
        }
    }

    private void seedDevices(SeededUsers users) {
        device(users.alice(), "fp_alice_chrome_win",  DeviceType.DESKTOP, "Chrome",  "Windows 11", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0", "203.0.113.10");
        device(users.bob(),   "fp_bob_safari_mac",    DeviceType.DESKTOP, "Safari",  "macOS 14",   "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_4) AppleWebKit/605.1.15 Safari/605.1.15", "203.0.113.20");
        device(users.carol(), "fp_carol_mobile_android", DeviceType.MOBILE, "Chrome Mobile", "Android 14", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Chrome/124.0 Mobile", "203.0.113.30");
    }

    private void device(User user, String fingerprint, DeviceType type, String browser, String os, String ua, String ip) {
        if (deviceRepository.findByUserIdAndFingerprint(user.getId(), fingerprint).isPresent()) return;
        UserDevice d = new UserDevice();
        d.setUser(user);
        d.setFingerprint(fingerprint);
        d.setDeviceType(type);
        d.setBrowser(browser);
        d.setOs(os);
        d.setUserAgent(ua);
        d.setLastIp(ip);
        d.setLastSeenAt(Instant.now());
        deviceRepository.save(d);
    }

    private void seedPaymentMethods(SeededUsers users) {
        paymentMethod(users.alice(), "pm_test_alice_4242", "cus_test_alice",  "visa", "4242", 12, 2027, true);
        paymentMethod(users.bob(),   "pm_test_bob_1234",   "cus_test_bob",    "mastercard", "1234", 9, 2026, true);
        paymentMethod(users.carol(), "pm_test_carol_5678", "cus_test_carol",  "visa", "5678", 3, 2028, true);
    }

    private void paymentMethod(User user, String stripeId, String customerId, String brand,
                                String last4, int expMonth, int expYear, boolean isDefault) {
        if (paymentMethodRepository.findByStripePaymentMethodId(stripeId).isPresent()) return;
        SavedPaymentMethod pm = new SavedPaymentMethod();
        pm.setUser(user);
        pm.setStripePaymentMethodId(stripeId);
        pm.setStripeCustomerId(customerId);
        pm.setBrand(brand);
        pm.setLast4(last4);
        pm.setExpMonth(expMonth);
        pm.setExpYear(expYear);
        pm.setDefault(isDefault);
        paymentMethodRepository.save(pm);
    }

    private void seedCredits(SeededUsers users) {
        credit(users.alice(), users.admin(), 1500L, "Referral bonus — referred Bob Martinez");
        credit(users.carol(), users.admin(), 1000L, "Loyalty cashback — Q1 2026 reward");
    }

    private void credit(User user, User issuedBy, long amountCents, String reason) {
        if (creditRepository.findAll().stream()
                .anyMatch(c -> c.getUser().getId().equals(user.getId()) && c.getReason() != null && c.getReason().equals(reason))) return;
        CustomerCredit c = new CustomerCredit();
        c.setUser(user);
        c.setAmountCents(amountCents);
        c.setCurrency("USD");
        c.setType(CreditEntryType.MANUAL_ADJUSTMENT);
        c.setReason(reason);
        c.setIssuedBy(issuedBy);
        creditRepository.save(c);
    }
}
