package backend.seeds;

import backend.models.core.CustomerAddress;
import backend.models.core.User;
import backend.repositories.CustomerAddressRepository;
import backend.seeds.UserSeeder.SeededUsers;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Seeds customer addresses for the 3 dev customer accounts.
 *
 * Alice  — 2 addresses (home Seattle default, work San Francisco)
 * Bob    — 1 address  (home New York, default)
 * Carol  — 2 addresses (home Austin default, apartment Austin)
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
public class AddressSeeder {

    private final CustomerAddressRepository addressRepository;

    public void seed(SeededUsers users) {
        seedAlice(users.alice());
        seedBob(users.bob());
        seedCarol(users.carol());
    }

    // =========================================================================
    // Alice Johnson — VIP customer, primary shopper on TechGadgets (SF-based)
    // =========================================================================

    private void seedAlice(User user) {
        if (!addressRepository.findAllByUserIdOrderByIsDefaultDescCreatedAtAsc(user.getId()).isEmpty()) return;

        address(user, "Home",
                "Alice Johnson",
                "412 Pine Street", null,
                "Seattle", "WA", "98101", "US",
                "+1 206-555-0182", true);

        address(user, "Work",
                "Alice Johnson",
                "1 Market Street", "Suite 300",
                "San Francisco", "CA", "94105", "US",
                "+1 415-555-0147", false);
    }

    // =========================================================================
    // Bob Martinez — new customer, shops at StyleHub (NY-based)
    // =========================================================================

    private void seedBob(User user) {
        if (!addressRepository.findAllByUserIdOrderByIsDefaultDescCreatedAtAsc(user.getId()).isEmpty()) return;

        address(user, "Home",
                "Bob Martinez",
                "230 West 56th Street", "Apt 8C",
                "New York", "NY", "10019", "US",
                "+1 212-555-0193", true);
    }

    // =========================================================================
    // Carol Chen — frequent buyer, shops at WellnessWorld (Austin-based)
    // =========================================================================

    private void seedCarol(User user) {
        if (!addressRepository.findAllByUserIdOrderByIsDefaultDescCreatedAtAsc(user.getId()).isEmpty()) return;

        address(user, "Home",
                "Carol Chen",
                "3801 South Lamar Boulevard", null,
                "Austin", "TX", "78704", "US",
                "+1 512-555-0164", true);

        address(user, "Work",
                "Carol Chen",
                "500 West 2nd Street", "Floor 19",
                "Austin", "TX", "78701", "US",
                "+1 512-555-0121", false);
    }

    // =========================================================================

    private void address(User user, String label, String recipientName,
                         String street, String street2,
                         String city, String state, String postalCode, String country,
                         String phoneNumber, boolean isDefault) {
        CustomerAddress a = new CustomerAddress();
        a.setUser(user);
        a.setLabel(label);
        a.setRecipientName(recipientName);
        a.setStreet(street);
        a.setStreet2(street2);
        a.setCity(city);
        a.setState(state);
        a.setPostalCode(postalCode);
        a.setCountry(country);
        a.setPhoneNumber(phoneNumber);
        a.setDefault(isDefault);
        addressRepository.save(a);
    }
}
