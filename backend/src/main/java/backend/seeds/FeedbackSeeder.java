package backend.seeds;

import backend.models.core.PlatformFeedback;
import backend.models.enums.FeedbackCategory;
import backend.models.enums.FeedbackStatus;
import backend.repositories.PlatformFeedbackRepository;
import backend.seeds.UserSeeder.SeededUsers;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class FeedbackSeeder {

    private final PlatformFeedbackRepository feedbackRepository;

    public void seed(SeededUsers users) {
        if (feedbackRepository.count() > 0) return;

        // Alice: bug report about checkout
        PlatformFeedback f1 = new PlatformFeedback();
        f1.setSubmittedBy(users.alice());
        f1.setCategory(FeedbackCategory.BUG_REPORT);
        f1.setMessage("The checkout page freezes when I try to apply a coupon code on mobile. " +
                "I have to refresh the whole page to continue.");
        f1.setRating(2);
        f1.setPageContext("/checkout");
        f1.setStatus(FeedbackStatus.UNDER_REVIEW);
        f1.setReviewedAt(Instant.now().minus(2, ChronoUnit.DAYS));
        f1.setReviewedById(users.admin().getId());
        feedbackRepository.save(f1);

        // Bob: feature request for wishlist sharing
        PlatformFeedback f2 = new PlatformFeedback();
        f2.setSubmittedBy(users.bob());
        f2.setCategory(FeedbackCategory.FEATURE_REQUEST);
        f2.setMessage("It would be great to be able to share my wishlist with friends via a direct link. " +
                "Currently I have to screenshot everything.");
        f2.setRating(4);
        f2.setPageContext("/lists");
        f2.setStatus(FeedbackStatus.OPEN);
        feedbackRepository.save(f2);

        // Carol: positive feedback about search
        PlatformFeedback f3 = new PlatformFeedback();
        f3.setSubmittedBy(users.carol());
        f3.setCategory(FeedbackCategory.SEARCH);
        f3.setMessage("The search autocomplete is really fast and accurate. " +
                "I can find products much more easily than before.");
        f3.setRating(5);
        f3.setPageContext("/browse");
        f3.setStatus(FeedbackStatus.RESOLVED);
        f3.setReviewedAt(Instant.now().minus(5, ChronoUnit.DAYS));
        f3.setReviewedById(users.admin().getId());
        feedbackRepository.save(f3);

        // Alice: UI/UX feedback
        PlatformFeedback f4 = new PlatformFeedback();
        f4.setSubmittedBy(users.alice());
        f4.setCategory(FeedbackCategory.UI_UX);
        f4.setMessage("The product images could be larger on the product detail page. " +
                "It's hard to see the fine details on smaller screens.");
        f4.setRating(3);
        f4.setPageContext("/products");
        f4.setStatus(FeedbackStatus.OPEN);
        feedbackRepository.save(f4);

        // Tech merchant: performance feedback
        PlatformFeedback f5 = new PlatformFeedback();
        f5.setSubmittedBy(users.techMerchant());
        f5.setCategory(FeedbackCategory.PERFORMANCE);
        f5.setMessage("The admin dashboard takes a long time to load when I have more than 200 products. " +
                "Filtering by category helps but the initial load is slow.");
        f5.setRating(3);
        f5.setPageContext("/admin/products");
        f5.setStatus(FeedbackStatus.OPEN);
        feedbackRepository.save(f5);
    }
}
