package backend.seeds;

import backend.models.core.Order;
import backend.models.core.OrderItem;
import backend.models.core.Product;
import backend.models.core.User;
import backend.models.enums.CancellationReason;
import backend.models.enums.FulfillmentMethod;
import backend.models.enums.FulfillmentStatus;
import backend.models.enums.OrderStatus;
import backend.repositories.OrderRepository;
import backend.seeds.UserSeeder.SeededUsers;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class OrderSeeder {

    private final OrderRepository orderRepository;

    public record SeededOrders(
            Order alice1, Order alice2, Order alice3, Order alice4,
            Order bob1, Order bob2,
            Order carol1, Order carol2
    ) {}

    public SeededOrders seed(SeededUsers users,
                             List<Product> tech, List<Product> style,
                             List<Product> wellness, List<Product> home, List<Product> sport) {

        if (orderRepository.countByUserId(users.alice().getId()) > 0) {
            // Already seeded — load and return in expected order
            var allAlice = orderRepository.findAll().stream()
                    .filter(o -> o.getUser().getId().equals(users.alice().getId()))
                    .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                    .toList();
            var allBob = orderRepository.findAll().stream()
                    .filter(o -> o.getUser().getId().equals(users.bob().getId()))
                    .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                    .toList();
            var allCarol = orderRepository.findAll().stream()
                    .filter(o -> o.getUser().getId().equals(users.carol().getId()))
                    .sorted((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                    .toList();
            return new SeededOrders(
                    allAlice.get(0), allAlice.get(1), allAlice.get(2), allAlice.get(3),
                    allBob.get(0), allBob.get(1),
                    allCarol.get(0), allCarol.get(1));
        }

        Instant now = Instant.now();

        // Order 1: Alice / TechGadgets / DELIVERED — headphones + USB hub
        Product headphones = find(tech, "Wireless Noise");
        Product usbHub     = find(tech, "USB Hub");
        Order alice1 = order(users.alice(), "pi_test_alice_001", OrderStatus.DELIVERED,
                now.minus(60, ChronoUnit.DAYS), now.minus(60, ChronoUnit.DAYS),
                now.minus(59, ChronoUnit.DAYS), now.minus(57, ChronoUnit.DAYS), now.minus(54, ChronoUnit.DAYS),
                null, null,
                item(headphones, 1), item(usbHub, 2));

        // Order 2: Alice / WellnessWorld / DELIVERED — protein + serum
        Product protein = find(wellness, "Whey Protein");
        Product serum   = find(wellness, "Vitamin C Brightening");
        Order alice2 = order(users.alice(), "pi_test_alice_002", OrderStatus.DELIVERED,
                now.minus(45, ChronoUnit.DAYS), now.minus(45, ChronoUnit.DAYS),
                now.minus(44, ChronoUnit.DAYS), now.minus(42, ChronoUnit.DAYS), now.minus(39, ChronoUnit.DAYS),
                null, null,
                item(protein, 2), item(serum, 1));

        // Order 3: Alice / SportZone / SHIPPED — GPS watch
        Product gpsWatch = find(sport, "GPS Running");
        Order alice3 = order(users.alice(), "pi_test_alice_003", OrderStatus.SHIPPED,
                now.minus(10, ChronoUnit.DAYS), now.minus(10, ChronoUnit.DAYS),
                now.minus(9, ChronoUnit.DAYS), now.minus(7, ChronoUnit.DAYS), null,
                null, null,
                item(gpsWatch, 1));

        // Order 4: Alice / TechGadgets / PAID — keyboard + webcam + stand
        Product keyboard = find(tech, "Mechanical Keyboard");
        Product webcam   = find(tech, "4K Webcam");
        Product stand    = find(tech, "Laptop Stand");
        Order alice4 = order(users.alice(), "pi_test_alice_004", OrderStatus.PAID,
                now.minus(2, ChronoUnit.DAYS), now.minus(2, ChronoUnit.DAYS),
                null, null, null,
                null, null,
                item(keyboard, 1), item(webcam, 1), item(stand, 1));

        // Order 5: Bob / StyleHub / DELIVERED — jeans + hoodie
        Product jeans  = find(style, "Slim-Fit Stretch Denim");
        Product hoodie = find(style, "Heavyweight Zip Hoodie");
        Order bob1 = order(users.bob(), "pi_test_bob_001", OrderStatus.DELIVERED,
                now.minus(30, ChronoUnit.DAYS), now.minus(30, ChronoUnit.DAYS),
                now.minus(29, ChronoUnit.DAYS), now.minus(27, ChronoUnit.DAYS), now.minus(24, ChronoUnit.DAYS),
                null, null,
                item(jeans, 1), item(hoodie, 1));

        // Order 6: Bob / HomeNest / PAID — smart thermostat
        Product thermostat = find(home, "Smart Thermostat");
        Order bob2 = order(users.bob(), "pi_test_bob_002", OrderStatus.PAID,
                now.minus(1, ChronoUnit.DAYS), now.minus(1, ChronoUnit.DAYS),
                null, null, null,
                null, null,
                item(thermostat, 1));

        // Order 7: Carol / WellnessWorld / DELIVERED — yoga mat + vitamins
        Product yogaMat  = find(wellness, "Yoga Mat Premium");
        Product vitaminC = find(wellness, "Vitamin C Brightening");
        Order carol1 = order(users.carol(), "pi_test_carol_001", OrderStatus.DELIVERED,
                now.minus(20, ChronoUnit.DAYS), now.minus(20, ChronoUnit.DAYS),
                now.minus(19, ChronoUnit.DAYS), now.minus(17, ChronoUnit.DAYS), now.minus(14, ChronoUnit.DAYS),
                null, null,
                item(yogaMat, 1), item(vitaminC, 1));

        // Order 8: Carol / TechGadgets / CANCELLED — VR headset
        Product vrHeadset = find(tech, "VR Headset");
        Order carol2 = order(users.carol(), "pi_test_carol_002", OrderStatus.CANCELLED,
                now.minus(5, ChronoUnit.DAYS), null,
                null, null, null,
                now.minus(5, ChronoUnit.DAYS), CancellationReason.CUSTOMER_REQUEST,
                item(vrHeadset, 1));

        return new SeededOrders(alice1, alice2, alice3, alice4, bob1, bob2, carol1, carol2);
    }

    private Order order(User user, String paymentIntentId, OrderStatus status,
                        Instant createdAt, Instant paidAt,
                        Instant packedAt, Instant shippedAt, Instant deliveredAt,
                        Instant cancelledAt, CancellationReason cancellationReason,
                        OrderItem... items) {
        Order o = new Order();
        o.setUser(user);
        o.setPaymentIntentId(paymentIntentId);
        o.setStatus(status);
        o.setCurrency("USD");
        o.setVersion(0L);

        BigDecimal total = BigDecimal.ZERO;
        for (OrderItem item : items) {
            item.setOrder(o);
            o.getItems().add(item);
            total = total.add(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        o.setTotalAmount(total);

        if (status == OrderStatus.SHIPPED) {
            o.setTrackingNumber("TRK" + paymentIntentId.substring(paymentIntentId.length() - 6).toUpperCase());
            o.setCarrier("UPS");
        }

        o.setPaidAt(paidAt);
        o.setPackedAt(packedAt);
        o.setShippedAt(shippedAt);
        o.setDeliveredAt(deliveredAt);
        o.setCancelledAt(cancelledAt);
        o.setCancellationReason(cancellationReason);

        Order saved = orderRepository.save(o);
        return saved;
    }

    private OrderItem item(Product product, int qty) {
        OrderItem i = new OrderItem();
        i.setProduct(product);
        i.setProductName(product.getName());
        i.setQuantity(qty);
        i.setUnitPrice(product.getPrice());
        i.setFulfillmentMethod(FulfillmentMethod.DELIVERY);
        i.setFulfillmentStatus(FulfillmentStatus.PENDING);
        return i;
    }

    private Product find(List<Product> products, String nameFragment) {
        return products.stream()
                .filter(p -> p.getName().contains(nameFragment))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No product matching: " + nameFragment));
    }
}
