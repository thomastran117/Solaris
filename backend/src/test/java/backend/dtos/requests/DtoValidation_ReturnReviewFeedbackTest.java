package backend.dtos.requests;

import backend.dtos.requests.feedback.SubmitFeedbackRequest;
import backend.dtos.requests.return_.BuyerInitiateReturnRequest;
import backend.dtos.requests.return_.BuyerReturnItemRequest;
import backend.dtos.requests.return_.MerchantRejectReturnRequest;
import backend.dtos.requests.review.CreateReviewRequest;
import backend.models.enums.FeedbackCategory;
import backend.models.enums.ReturnReason;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

class DtoValidation_ReturnReviewFeedbackTest extends AbstractDtoValidationTest {

    // ─── BuyerInitiateReturnRequest ───────────────────────────────────────────

    @Test
    void buyerReturn_valid_noViolations() {
        assertValid(new BuyerInitiateReturnRequest(
                List.of(new BuyerReturnItemRequest(UUID.randomUUID(), 1)),
                ReturnReason.CHANGED_MIND, "I changed my mind", null));
    }

    @Test
    void buyerReturn_nullItems_violation() {
        assertViolation(
                new BuyerInitiateReturnRequest(null, ReturnReason.CHANGED_MIND, "note", null),
                "items");
    }

    @Test
    void buyerReturn_emptyItems_violation() {
        assertViolation(
                new BuyerInitiateReturnRequest(List.of(), ReturnReason.CHANGED_MIND, "note", null),
                "items");
    }

    @Test
    void buyerReturn_nullReason_violation() {
        assertViolation(
                new BuyerInitiateReturnRequest(
                        List.of(new BuyerReturnItemRequest(UUID.randomUUID(), 1)),
                        null, "note", null),
                "reason");
    }

    // ─── BuyerReturnItemRequest ───────────────────────────────────────────────

    @Test
    void buyerReturnItem_valid_noViolations() {
        assertValid(new BuyerReturnItemRequest(UUID.randomUUID(), 2));
    }

    @Test
    void buyerReturnItem_nullOrderItemId_violation() {
        assertViolation(new BuyerReturnItemRequest(null, 1), "orderItemId");
    }

    @Test
    void buyerReturnItem_zeroQuantity_violation() {
        assertViolation(new BuyerReturnItemRequest(UUID.randomUUID(), 0), "quantityToReturn");
    }

    // ─── MerchantRejectReturnRequest ──────────────────────────────────────────

    @Test
    void merchantReject_valid_noViolations() {
        assertValid(new MerchantRejectReturnRequest("Policy violation"));
    }

    @Test
    void merchantReject_blankNote_violation() {
        assertViolation(new MerchantRejectReturnRequest(""), "merchantNote");
    }

    @Test
    void merchantReject_noteTooLong_violation() {
        assertViolation(new MerchantRejectReturnRequest("A".repeat(1001)), "merchantNote");
    }

    // ─── CreateReviewRequest ──────────────────────────────────────────────────

    @Test
    void createReview_valid_noViolations() {
        CreateReviewRequest req = new CreateReviewRequest();
        req.setRating(4);
        assertValid(req);
    }

    @Test
    void createReview_nullRating_violation() {
        CreateReviewRequest req = new CreateReviewRequest();
        req.setRating(null);
        assertViolation(req, "rating");
    }

    @Test
    void createReview_ratingTooLow_violation() {
        CreateReviewRequest req = new CreateReviewRequest();
        req.setRating(0);
        assertViolation(req, "rating");
    }

    @Test
    void createReview_ratingTooHigh_violation() {
        CreateReviewRequest req = new CreateReviewRequest();
        req.setRating(6);
        assertViolation(req, "rating");
    }

    @Test
    void createReview_bodyWithHtml_safeTextViolation() {
        CreateReviewRequest req = new CreateReviewRequest();
        req.setRating(3);
        req.setBody("<script>evil()</script>");
        assertViolation(req, "body");
    }

    @Test
    void createReview_titleTooLong_violation() {
        CreateReviewRequest req = new CreateReviewRequest();
        req.setRating(3);
        req.setTitle("T".repeat(256));
        assertViolation(req, "title");
    }

    // ─── SubmitFeedbackRequest ────────────────────────────────────────────────

    @Test
    void submitFeedback_valid_noViolations() {
        assertValid(new SubmitFeedbackRequest(
                FeedbackCategory.BUG_REPORT,
                "This feature does not work correctly on mobile devices",
                4, "/checkout"));
    }

    @Test
    void submitFeedback_nullCategory_violation() {
        assertViolation(new SubmitFeedbackRequest(
                null, "Long enough feedback message here.", null, null), "category");
    }

    @Test
    void submitFeedback_blankMessage_violation() {
        assertViolation(new SubmitFeedbackRequest(
                FeedbackCategory.FEATURE_REQUEST, "", null, null), "message");
    }

    @Test
    void submitFeedback_messageTooShort_violation() {
        assertViolation(new SubmitFeedbackRequest(
                FeedbackCategory.BUG_REPORT, "Short", null, null), "message"); // min 10 chars
    }

    @Test
    void submitFeedback_messageTooLong_violation() {
        assertViolation(new SubmitFeedbackRequest(
                FeedbackCategory.BUG_REPORT, "A".repeat(5001), null, null), "message");
    }

    @Test
    void submitFeedback_ratingTooHigh_violation() {
        assertViolation(new SubmitFeedbackRequest(
                FeedbackCategory.BUG_REPORT, "Long enough message here.", 6, null), "rating");
    }

    @Test
    void submitFeedback_ratingTooLow_violation() {
        assertViolation(new SubmitFeedbackRequest(
                FeedbackCategory.BUG_REPORT, "Long enough message here.", 0, null), "rating");
    }

    @Test
    void submitFeedback_nullRatingAllowed_noViolations() {
        assertValid(new SubmitFeedbackRequest(
                FeedbackCategory.OTHER, "Long enough feedback message here.", null, null));
    }

    @Test
    void submitFeedback_messageWithHtml_safeTextViolation() {
        assertViolation(new SubmitFeedbackRequest(
                FeedbackCategory.BUG_REPORT, "<script>alert('xss')</script>", null, null), "message");
    }
}
