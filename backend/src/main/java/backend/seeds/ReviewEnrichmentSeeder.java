package backend.seeds;

import backend.models.core.InternalNote;
import backend.models.core.ProductReview;
import backend.models.core.ReviewVote;
import backend.models.core.User;
import backend.models.enums.NoteEntityType;
import backend.models.core.ProductReview;
import backend.models.enums.ReviewStatus;
import backend.repositories.InternalNoteRepository;
import backend.repositories.ProductReviewRepository;
import backend.repositories.ReviewVoteRepository;
import backend.seeds.OrderSeeder.SeededOrders;
import backend.seeds.UserSeeder.SeededUsers;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class ReviewEnrichmentSeeder {

    private final ProductReviewRepository reviewRepository;
    private final ReviewVoteRepository voteRepository;
    private final InternalNoteRepository noteRepository;

    public void seed(SeededUsers users, SeededOrders orders,
                     List<backend.models.core.Product> tech,
                     List<backend.models.core.Product> wellness) {

        if (voteRepository.count() > 0) return;

        seedVotes(users, tech, wellness);
        seedNotes(users, orders);
    }

    private void seedVotes(SeededUsers users, List<backend.models.core.Product> tech,
                           List<backend.models.core.Product> wellness) {
        // Pull a handful of published reviews from TechGadgets and WellnessWorld products
        tech.stream().limit(3).forEach(product -> {
            List<ProductReview> reviews = reviewRepository.findAllByProductIdAndStatus(
                    product.getId(), ReviewStatus.PUBLISHED, PageRequest.of(0, 2)).getContent();
            reviews.forEach(review -> {
                // Cross-vote: bob and carol vote on each other's reviews
                if (!review.getReviewer().getId().equals(users.bob().getId())) {
                    vote(review, users.bob().getId());
                }
                if (!review.getReviewer().getId().equals(users.carol().getId())) {
                    vote(review, users.carol().getId());
                }
            });
        });

        wellness.stream().limit(2).forEach(product -> {
            List<ProductReview> reviews = reviewRepository.findAllByProductIdAndStatus(
                    product.getId(), ReviewStatus.PUBLISHED, PageRequest.of(0, 2)).getContent();
            reviews.forEach(review -> {
                if (!review.getReviewer().getId().equals(users.alice().getId())) {
                    vote(review, users.alice().getId());
                }
            });
        });
    }

    private void vote(ProductReview review, UUID userId) {
        if (voteRepository.existsByReviewIdAndUserId(review.getId(), userId)) return;
        ReviewVote v = new ReviewVote();
        v.setReviewId(review.getId());
        v.setUserId(userId);
        voteRepository.save(v);
    }

    private void seedNotes(SeededUsers users, SeededOrders orders) {
        if (noteRepository.count() > 0) return;
        internalNote(users.admin(), NoteEntityType.ORDER, orders.alice3().getId(),
                "VIP customer — prioritise fulfilment and proactively notify on any delay.");
        internalNote(users.admin(), NoteEntityType.ORDER, orders.carol1().getId(),
                "Price match applied at checkout. Do not auto-refund any price diff.");
    }

    private void internalNote(User author, NoteEntityType entityType, UUID entityId, String body) {
        InternalNote note = new InternalNote();
        note.setAuthor(author);
        note.setEntityType(entityType);
        note.setEntityId(entityId);
        note.setBody(body);
        noteRepository.save(note);
    }
}
