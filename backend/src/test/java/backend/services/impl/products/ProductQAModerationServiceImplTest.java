package backend.services.impl.products;

import backend.dtos.requests.qa.ModerateQARequest;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.ProductAnswer;
import backend.models.core.ProductQuestion;
import backend.models.enums.QAReportType;
import backend.models.enums.QAStatus;
import backend.repositories.ProductAnswerRepository;
import backend.repositories.ProductQuestionRepository;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductQAModerationServiceImplTest {

    private static final UUID TARGET_ID = TestIds.uuid(1);

    private ProductQuestionRepository questionRepository;
    private ProductAnswerRepository   answerRepository;

    private ProductQAModerationServiceImpl service;

    @BeforeEach
    void setUp() {
        questionRepository = mock(ProductQuestionRepository.class);
        answerRepository   = mock(ProductAnswerRepository.class);
        service = new ProductQAModerationServiceImpl(questionRepository, answerRepository);
    }

    @Test
    void moderateContent_invalidAction_throwsBadRequestException() {
        ModerateQARequest req = new ModerateQARequest();
        req.setAction("INVALID");

        assertThrows(BadRequestException.class, () ->
                service.moderateContent(QAReportType.QUESTION, TARGET_ID, req));
    }

    @Test
    void moderateContent_approveQuestion_updatesStatusToVisible() {
        when(questionRepository.findById(TARGET_ID)).thenReturn(Optional.of(new ProductQuestion()));

        service.moderateContent(QAReportType.QUESTION, TARGET_ID, approve());

        verify(questionRepository).updateStatusIfDifferent(TARGET_ID, QAStatus.VISIBLE.name());
        verify(answerRepository, never()).updateStatusIfDifferent(TARGET_ID, QAStatus.VISIBLE.name());
    }

    @Test
    void moderateContent_rejectQuestion_updatesStatusToHidden() {
        when(questionRepository.findById(TARGET_ID)).thenReturn(Optional.of(new ProductQuestion()));

        service.moderateContent(QAReportType.QUESTION, TARGET_ID, reject());

        verify(questionRepository).updateStatusIfDifferent(TARGET_ID, QAStatus.HIDDEN.name());
    }

    @Test
    void moderateContent_approveAnswer_updatesAnswerRepository() {
        when(answerRepository.findById(TARGET_ID)).thenReturn(Optional.of(new ProductAnswer()));

        service.moderateContent(QAReportType.ANSWER, TARGET_ID, approve());

        verify(answerRepository).updateStatusIfDifferent(TARGET_ID, QAStatus.VISIBLE.name());
        verify(questionRepository, never()).updateStatusIfDifferent(TARGET_ID, QAStatus.VISIBLE.name());
    }

    @Test
    void moderateContent_questionNotFound_throwsResourceNotFoundException() {
        when(questionRepository.findById(TARGET_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                service.moderateContent(QAReportType.QUESTION, TARGET_ID, approve()));
    }

    @Test
    void moderateContent_answerNotFound_throwsResourceNotFoundException() {
        when(answerRepository.findById(TARGET_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                service.moderateContent(QAReportType.ANSWER, TARGET_ID, approve()));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static ModerateQARequest approve() {
        ModerateQARequest r = new ModerateQARequest();
        r.setAction("APPROVE");
        return r;
    }

    private static ModerateQARequest reject() {
        ModerateQARequest r = new ModerateQARequest();
        r.setAction("REJECT");
        return r;
    }
}
