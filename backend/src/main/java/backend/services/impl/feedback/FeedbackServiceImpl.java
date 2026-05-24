package backend.services.impl.feedback;

import backend.dtos.requests.feedback.SubmitFeedbackRequest;
import backend.dtos.requests.feedback.UpdateFeedbackStatusRequest;
import backend.dtos.responses.feedback.FeedbackResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.PlatformFeedback;
import backend.models.core.User;
import backend.models.enums.FeedbackCategory;
import backend.models.enums.FeedbackStatus;
import backend.repositories.PlatformFeedbackRepository;
import backend.repositories.UserRepository;
import backend.services.intf.feedback.FeedbackService;
import backend.utilities.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    private final PlatformFeedbackRepository feedbackRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public FeedbackResponse submitFeedback(UUID userId, SubmitFeedbackRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        PlatformFeedback feedback = new PlatformFeedback();
        feedback.setSubmittedBy(user);
        feedback.setCategory(request.category());
        feedback.setMessage(request.message());
        feedback.setRating(request.rating());
        feedback.setPageContext(request.pageContext());
        feedback.setStatus(FeedbackStatus.OPEN);

        return toResponse(feedbackRepository.save(feedback));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<FeedbackResponse> getMyFeedback(UUID userId, int page, int size) {
        return new PagedResponse<>(
                feedbackRepository.findAllBySubmittedByIdOrderByCreatedAtDesc(
                        userId, PageRequest.of(page, size))
                        .map(this::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<FeedbackResponse> listAllFeedback(UUID adminUserId,
                                                           FeedbackStatus status,
                                                           FeedbackCategory category,
                                                           int page, int size) {
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        SecurityUtils.requireAdmin(admin);

        return new PagedResponse<>(
                feedbackRepository.findAllByFilters(status, category, PageRequest.of(page, size))
                        .map(this::toResponse));
    }

    @Override
    @Transactional
    public FeedbackResponse updateFeedbackStatus(UUID feedbackId, UUID adminUserId,
                                                 UpdateFeedbackStatusRequest request) {
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        SecurityUtils.requireAdmin(admin);

        PlatformFeedback feedback = feedbackRepository.findById(feedbackId)
                .orElseThrow(() -> new ResourceNotFoundException("Feedback not found"));

        feedback.setStatus(request.status());
        feedback.setReviewedById(adminUserId);
        if (feedback.getReviewedAt() == null) {
            feedback.setReviewedAt(Instant.now());
        }

        return toResponse(feedbackRepository.save(feedback));
    }

    private FeedbackResponse toResponse(PlatformFeedback f) {
        User u = f.getSubmittedBy();
        String name = (u.getFirstName() != null && u.getLastName() != null)
                ? u.getFirstName() + " " + u.getLastName() : null;
        return new FeedbackResponse(
                f.getId(),
                u.getId(),
                u.getEmail(),
                name,
                f.getCategory().name(),
                f.getStatus().name(),
                f.getMessage(),
                f.getRating(),
                f.getPageContext(),
                f.getReviewedAt(),
                f.getReviewedById(),
                f.getCreatedAt(),
                f.getUpdatedAt()
        );
    }
}
