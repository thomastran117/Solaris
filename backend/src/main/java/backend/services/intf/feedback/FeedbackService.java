package backend.services.intf.feedback;

import backend.dtos.requests.feedback.SubmitFeedbackRequest;
import backend.dtos.requests.feedback.UpdateFeedbackStatusRequest;
import backend.dtos.responses.feedback.FeedbackResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.models.enums.FeedbackCategory;
import backend.models.enums.FeedbackStatus;

import java.util.UUID;

public interface FeedbackService {

    FeedbackResponse submitFeedback(UUID userId, SubmitFeedbackRequest request);

    PagedResponse<FeedbackResponse> getMyFeedback(UUID userId, int page, int size);

    PagedResponse<FeedbackResponse> listAllFeedback(UUID adminUserId,
                                                    FeedbackStatus status,
                                                    FeedbackCategory category,
                                                    int page, int size);

    FeedbackResponse updateFeedbackStatus(UUID feedbackId, UUID adminUserId,
                                          UpdateFeedbackStatusRequest request);
}
