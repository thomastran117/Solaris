package backend.seeds;

import backend.models.core.CustomerSegment;
import backend.models.core.User;
import backend.models.enums.UserRole;
import backend.models.enums.UserStatus;
import backend.repositories.CustomerSegmentRepository;
import backend.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Set;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class UserSeeder {

    private final UserRepository userRepository;
    private final CustomerSegmentRepository customerSegmentRepository;
    private final PasswordEncoder passwordEncoder;

    public record SeededUsers(
            User admin,
            User techMerchant,
            User styleMerchant,
            User wellnessMerchant,
            User homeMerchant,
            User sportMerchant,
            User alice,
            User bob,
            User carol
    ) {}

    public SeededUsers seed() {
        String hash = passwordEncoder.encode("Password123!");

        CustomerSegment vip      = seg("VIP", "VIP Customer", "High-value repeat customers");
        CustomerSegment newCust  = seg("NEW_CUSTOMER", "New Customer", "First-time shoppers");
        CustomerSegment frequent = seg("FREQUENT_BUYER", "Frequent Buyer", "Customers with 5+ orders");

        User admin            = user("Admin", "User", "admin@shopwave.dev", hash, UserRole.ADMIN, null);
        User techMerchant     = user("Marcus", "Lee", "merchant.tech@shopwave.dev", hash, UserRole.MERCHANT, LocalDate.of(1985, 3, 14));
        User styleMerchant    = user("Priya", "Sharma", "merchant.style@shopwave.dev", hash, UserRole.MERCHANT, LocalDate.of(1990, 7, 22));
        User wellnessMerchant = user("Jordan", "Kim", "merchant.wellness@shopwave.dev", hash, UserRole.MERCHANT, LocalDate.of(1988, 11, 5));
        User homeMerchant     = user("Alex", "Rivera", "merchant.home@shopwave.dev", hash, UserRole.MERCHANT, LocalDate.of(1982, 4, 30));
        User sportMerchant    = user("Sam", "Park", "merchant.sport@shopwave.dev", hash, UserRole.MERCHANT, LocalDate.of(1991, 8, 17));
        User alice = userWithSegments("Alice", "Johnson", "alice@example.com", hash, UserRole.USER, LocalDate.of(1995, 6, 18), Set.of(vip, frequent));
        User bob   = userWithSegments("Bob", "Martinez", "bob@example.com", hash, UserRole.USER, LocalDate.of(2000, 2, 28), Set.of(newCust));
        User carol = userWithSegments("Carol", "Chen", "carol@example.com", hash, UserRole.USER, LocalDate.of(1993, 9, 12), Set.of(frequent));

        return new SeededUsers(admin, techMerchant, styleMerchant, wellnessMerchant, homeMerchant, sportMerchant, alice, bob, carol);
    }

    private CustomerSegment seg(String code, String name, String description) {
        return customerSegmentRepository.findByCodeIgnoreCase(code).orElseGet(() -> {
            CustomerSegment s = new CustomerSegment();
            s.setCode(code);
            s.setName(name);
            s.setDescription(description);
            return customerSegmentRepository.save(s);
        });
    }

    private User user(String first, String last, String email, String hash, UserRole role, LocalDate dob) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            User u = new User();
            u.setFirstName(first);
            u.setLastName(last);
            u.setEmail(email);
            u.setPassword(hash);
            u.setRole(role);
            u.setStatus(UserStatus.ACTIVE);
            u.setBirthDate(dob);
            return userRepository.save(u);
        });
    }

    private User userWithSegments(String first, String last, String email, String hash,
                                   UserRole role, LocalDate dob, Set<CustomerSegment> segments) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            User u = new User();
            u.setFirstName(first);
            u.setLastName(last);
            u.setEmail(email);
            u.setPassword(hash);
            u.setRole(role);
            u.setStatus(UserStatus.ACTIVE);
            u.setBirthDate(dob);
            u.setSegments(segments);
            return userRepository.save(u);
        });
    }
}
