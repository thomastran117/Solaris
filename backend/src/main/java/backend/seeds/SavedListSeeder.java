package backend.seeds;

import backend.models.core.Product;
import backend.models.core.SavedList;
import backend.models.core.SavedListItem;
import backend.models.core.SavedListType;
import backend.models.core.User;
import backend.repositories.SavedListRepository;
import backend.seeds.UserSeeder.SeededUsers;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.List;

/**
 * Seeds saved lists for the 3 dev customer accounts, exercising every
 * {@link SavedListType} so the frontend has something to show out of the box.
 *
 * Alice  — WISHLIST "Dream Tech Setup" (public), PROJECT "Gaming PC Build" (public)
 * Bob    — WISHLIST "Style Picks" (public), GIFT "Birthday gifts for Alex" (private)
 * Carol  — SHOPPING "Wellness Restock" (private), WISHLIST "Wellness Goals" (private)
 */
@Component
@Profile("dev")
@RequiredArgsConstructor
public class SavedListSeeder {

    private static final String SLUG_ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RNG = new SecureRandom();

    private final SavedListRepository savedListRepository;

    public void seed(SeededUsers users,
                     List<Product> tech, List<Product> style, List<Product> wellness) {
        seedAlice(users.alice(), tech);
        seedBob(users.bob(), style);
        seedCarol(users.carol(), wellness);
    }

    // =========================================================================
    // Alice Johnson — tech enthusiast
    // =========================================================================

    private void seedAlice(User user, List<Product> tech) {
        if (!savedListRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId()).isEmpty()) return;

        SavedList wishlist = newList(user, "Dream Tech Setup", SavedListType.WISHLIST,
                "Big-ticket tech I'd love to own one day.", true);
        addItem(wishlist, tech.get(1), 1, null, false);   // Smart Watch Series X
        addItem(wishlist, tech.get(5), 1, null, false);   // 4K Webcam Pro
        addItem(wishlist, tech.get(7), 1, null, false);   // Noise-Cancelling Earbuds
        addItem(wishlist, tech.get(11), 1, null, false);  // Portable SSD 1TB
        savedListRepository.save(wishlist);

        SavedList project = newList(user, "Gaming PC Build", SavedListType.PROJECT,
                "Parts list for my 2026 build. Marking off as I buy them.", true);
        addItem(project, tech.get(20), 1, "Closed-back, leather earcups", true);  // Gaming Headset
        addItem(project, tech.get(9), 1, "Wireless preferred", false);            // LED Gaming Mouse
        addItem(project, tech.get(24), 2, "One for desk + one spare", false);     // XXL Desk Mat
        savedListRepository.save(project);
    }

    // =========================================================================
    // Bob Martinez — style-conscious new customer
    // =========================================================================

    private void seedBob(User user, List<Product> style) {
        if (!savedListRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId()).isEmpty()) return;

        SavedList wishlist = newList(user, "Style Picks", SavedListType.WISHLIST,
                "My personal style refresh.", true);
        addItem(wishlist, style.get(0), 1, null, false);    // Premium Organic Cotton T-Shirt
        addItem(wishlist, style.get(1), 1, null, false);    // Slim-Fit Stretch Denim Jeans
        addItem(wishlist, style.get(4), 1, null, false);    // Classic Canvas Sneakers
        addItem(wishlist, style.get(13), 1, null, false);   // Puffer Jacket
        savedListRepository.save(wishlist);

        SavedList gift = newList(user, "Birthday gifts for Alex", SavedListType.GIFT,
                "Ideas for Alex's birthday in March.", false);
        addItem(gift, style.get(0), 1, "Size M", false);
        addItem(gift, style.get(4), 1, "Size 10", false);
        savedListRepository.save(gift);
    }

    // =========================================================================
    // Carol Chen — frequent wellness buyer
    // =========================================================================

    private void seedCarol(User user, List<Product> wellness) {
        if (!savedListRepository.findAllByUserIdOrderByCreatedAtDesc(user.getId()).isEmpty()) return;

        SavedList shopping = newList(user, "Wellness Restock", SavedListType.SHOPPING,
                "Monthly restock — buy together to save on shipping.", false);
        addItem(shopping, wellness.get(0), 2, "Vanilla flavour", true);  // Whey Protein
        addItem(shopping, wellness.get(10), 1, null, false);             // Sleep Gummies
        addItem(shopping, wellness.get(15), 1, null, false);             // LED Therapy Face Mask
        savedListRepository.save(shopping);

        SavedList wishlist = newList(user, "Wellness Goals", SavedListType.WISHLIST,
                "Long-term wellness equipment goals.", false);
        addItem(wishlist, wellness.get(5), 1, null, false);  // Yoga Mat Premium
        savedListRepository.save(wishlist);
    }

    // =========================================================================

    private SavedList newList(User user, String name, SavedListType type,
                              String description, boolean isPublic) {
        SavedList list = new SavedList();
        list.setUser(user);
        list.setName(name);
        list.setType(type);
        list.setDescription(description);
        list.setPublic(isPublic);
        if (isPublic) list.setShareSlug(randomSlug());
        return list;
    }

    private void addItem(SavedList list, Product product, int quantity, String note, boolean purchased) {
        SavedListItem item = new SavedListItem();
        item.setSavedList(list);
        item.setProduct(product);
        item.setVariant(null);
        item.setQuantity(quantity);
        item.setNote(note);
        item.setPurchased(purchased);
        if (purchased) item.setPurchasedAt(java.time.Instant.now());
        list.getItems().add(item);
    }

    private static String randomSlug() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(SLUG_ALPHABET.charAt(RNG.nextInt(SLUG_ALPHABET.length())));
        }
        return sb.toString();
    }
}
