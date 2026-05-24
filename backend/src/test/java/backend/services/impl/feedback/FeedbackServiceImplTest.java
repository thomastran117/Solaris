package backend.services.impl.feedback;

import backend.dtos.requests.feedback.SubmitFeedbackRequest;
import backend.dtos.requests.feedback.UpdateFeedbackStatusRequest;
import backend.dtos.responses.feedback.FeedbackResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.exceptions.http.ForbiddenException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.PlatformFeedback;
import backend.models.core.User;
import backend.models.enums.FeedbackCategory;
import backend.models.enums.FeedbackStatus;
import backend.models.enums.UserRole;
import backend.repositories.PlatformFeedbackRepository;
import backend.repositories.UserRepository;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FeedbackServiceImplTest {

    private static final UUID USER_ID     = TestIds.uuid(1);
    private static final UUID ADMIN_ID    = TestIds.uuid(2);
    private static final UUID FEEDBACK_ID = TestIds.uuid(3);

    private PlatformFeedbackRepository feedbackRepository;
    private UserRepository userRepository;

    private FeedbackServiceImpl service;

    @BeforeEach
    void setUp() {
        feedbackRepository = mock(PlatformFeedbackRepository.class);
        userRepository     = mock(UserRepository.class);

        service = new FeedbackServiceImpl(feedbackRepository, userRepository);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(makeUser(USER_ID, UserRole.USER)));
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(makeUser(ADMIN_ID, UserRole.ADMIN)));
        when(feedbackRepository.save(any(PlatformFeedback.class))).thenAnswer(inv -> {
            PlatformFeedback f = inv.getArgument(0);
            if (f.getId() == null) f.setId(FEEDBACK_ID);
            return f;
        });
    }

    // ─── submitFeedback ───────────────────────────────────────────────────────

    @Test
    void submitFeedback_happyPath_savesAndReturnsResponse() {
        SubmitFeedbackRequest req = new SubmitFeedbackRequest(
                FeedbackCategory.BUG_REPORT,
                "The checkout page freezes on mobile when applying a coupon.",
                3, "/checkout");

        FeedbackResponse result = service.submitFeedback(USER_ID, req);

        assertNotNull(result);
        assertEquals("BUG_REPORT", result.getCategory());
        assertEquals("OPEN", result.getStatus());
        assertEquals(3, result.getRating());
        verify(feedbackRepository).save(any(PlatformFeedback.class));
    }

    @Test
    void submitFeedback_userNotFound_throwsResourceNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        SubmitFeedbackRequest req = new SubmitFeedbackRequest(
                FeedbackCategory.OTHER, "Some feedback message here.", null, null);

        assertThrows(ResourceNotFoundException.class,
                () -> service.submitFeedback(USER_ID, req));
    }

    // ─── getMyFeedback ────────────────────────────────────────────────────────

    @Test
    void getMyFeedback_returnsPagedResponse() {
        PlatformFeedback f = makeFeedback(FeedbackStatus.OPEN);
        when(feedbackRepository.findAllBySubmittedByIdOrderByCreatedAtDesc(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(f)));

        PagedResponse<FeedbackResponse> result = service.getMyFeedback(USER_ID, 0, 20);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("BUG_REPORT", result.getItems().get(0).getCategory());
    }

    // ─── listAllFeedback ──────────────────────────────────────────────────────

    @Test
    void listAllFeedback_asAdmin_returnsPagedResponse() {
        PlatformFeedback f = makeFeedback(FeedbackStatus.OPEN);
        when(feedbackRepository.findAllByFilters(isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(f)));

        PagedResponse<FeedbackResponse> result =
                service.listAllFeedback(ADMIN_ID, null, null, 0, 20);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void listAllFeedback_asNonAdmin_throwsForbidden() {
        assertThrows(ForbiddenException.class,
                () -> service.listAllFeedback(USER_ID, null, null, 0, 20));
    }

    // ─── updateFeedbackStatus ─────────────────────────────────────────────────

    @Test
    void updateFeedbackStatus_happyPath_updatesStatusAndReturnsResponse() {
        PlatformFeedback f = makeFeedback(FeedbackStatus.OPEN);
        when(feedbackRepository.findById(FEEDBACK_ID)).thenReturn(Optional.of(f));

        UpdateFeedbackStatusRequest req = new UpdateFeedbackStatusRequest(FeedbackStatus.UNDER_REVIEW);
        FeedbackResponse result = service.updateFeedbackStatus(FEEDBACK_ID, ADMIN_ID, req);

        ArgumentCaptor<PlatformFeedback> captor = ArgumentCaptor.forClass(PlatformFeedback.class);
        verify(feedbackRepository).save(captor.capture());

        assertEquals(FeedbackStatus.UNDER_REVIEW, captor.getValue().getStatus());
        assertEquals(ADMIN_ID, captor.getValue().getReviewedById());
        assertNotNull(captor.getValue().getReviewedAt());
        assertNotNull(result);
    }

    @Test
    void updateFeedbackStatus_setsReviewedAt_onlyWhenNull() {
        Instant existingReviewedAt = Instant.now().minusSeconds(3600);
        PlatformFeedback f = makeFeedback(FeedbackStatus.UNDER_REVIEW);
        f.setReviewedAt(existingReviewedAt);
        when(feedbackRepository.findById(FEEDBACK_ID)).thenReturn(Optional.of(f));

        service.updateFeedbackStatus(FEEDBACK_ID, ADMIN_ID, new UpdateFeedbackStatusRequest(FeedbackStatus.RESOLVED));

        ArgumentCaptor<PlatformFeedback> captor = ArgumentCaptor.forClass(PlatformFeedback.class);
        verify(feedbackRepository).save(captor.capture());
        assertEquals(existingReviewedAt, captor.getValue().getReviewedAt());
    }

    @Test
    void updateFeedbackStatus_feedbackNotFound_throwsResourceNotFound() {
        when(feedbackRepository.findById(FEEDBACK_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.updateFeedbackStatus(FEEDBACK_ID, ADMIN_ID,
                        new UpdateFeedbackStatusRequest(FeedbackStatus.RESOLVED)));
    }

    @Test
    void updateFeedbackStatus_nonAdmin_throwsForbidden() {
        assertThrows(ForbiddenException.class,
                () -> service.updateFeedbackStatus(FEEDBACK_ID, USER_ID,
                        new UpdateFeedbackStatusRequest(FeedbackStatus.RESOLVED)));
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private User makeUser(UUID id, UserRole role) {
        User u = new User();
        u.setId(id);
        u.setFirstName("Test");
        u.setLastName("User");
        u.setEmail("test@example.com");
        u.setRole(role);
        return u;
    }

    private PlatformFeedback makeFeedback(FeedbackStatus status) {
        PlatformFeedback f = new PlatformFeedback();
        f.setId(FEEDBACK_ID);
        f.setSubmittedBy(makeUser(USER_ID, UserRole.USER));
        f.setCategory(FeedbackCategory.BUG_REPORT);
        f.setStatus(status);
        f.setMessage("The checkout page freezes on mobile when applying a coupon.");
        f.setPageContext("/checkout");
        return f;
    }
}
