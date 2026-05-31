package backend.seeds;

import backend.models.core.Product;
import backend.models.core.StockNotification;
import backend.models.core.User;
import backend.models.enums.NotificationStatus;
import backend.repositories.StockNotificationRepository;
import backend.seeds.UserSeeder.SeededUsers;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class StockNotificationSeeder {

    private final StockNotificationRepository notificationRepository;

    public void seed(SeededUsers users,
                     List<Product> tech, List<Product> style, List<Product> wellness) {

        // Bob: 2 PENDING notifications for StyleHub products
        style.stream().filter(p -> p.getName().contains("Chelsea Boots")).findFirst()
                .ifPresent(p -> notification(users.bob(), p, NotificationStatus.PENDING, null));
        style.stream().filter(p -> p.getName().contains("Satin Bomber")).findFirst()
                .ifPresent(p -> notification(users.bob(), p, NotificationStatus.PENDING, null));

        // Carol: 1 PENDING notification for a WellnessWorld product
        wellness.stream().filter(p -> p.getName().contains("HEPA Air")).findFirst()
                .ifPresent(p -> notification(users.carol(), p, NotificationStatus.PENDING, null));

        // Alice: 1 NOTIFIED notification for a TechGadgets product
        tech.stream().filter(p -> p.getName().contains("Portable Monitor")).findFirst()
                .ifPresent(p -> notification(users.alice(), p, NotificationStatus.NOTIFIED,
                        Instant.now().minus(2, ChronoUnit.DAYS)));
    }

    private void notification(User user, Product product, NotificationStatus status, Instant notifiedAt) {
        if (notificationRepository.findByUserIdAndProductIdAndVariantRef(
                user.getId(), product.getId(), null).isPresent()) return;
        StockNotification sn = new StockNotification();
        sn.setUser(user);
        sn.setProduct(product);
        sn.setStatus(status);
        sn.setNotifiedAt(notifiedAt);
        notificationRepository.save(sn);
    }
}
