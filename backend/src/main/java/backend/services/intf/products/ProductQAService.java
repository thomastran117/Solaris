package backend.services.intf.products;

import backend.dtos.requests.qa.AskQuestionRequest;
import backend.dtos.requests.qa.ReportQAContentRequest;
import backend.dtos.requests.qa.SubmitAnswerRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.qa.AnswerResponse;
import backend.dtos.responses.qa.QuestionResponse;
import backend.models.enums.QAReportType;

import java.util.UUID;

public interface ProductQAService {
    QuestionResponse askQuestion(UUID productId, UUID userId, AskQuestionRequest request);
    AnswerResponse answerQuestion(UUID questionId, UUID userId, SubmitAnswerRequest request);
    void upvoteAnswer(UUID questionId, UUID answerId, UUID userId);
    void reportContent(QAReportType type, UUID targetId, UUID userId, ReportQAContentRequest request);
    PagedResponse<QuestionResponse> getQuestionsForProduct(UUID productId, int page, int size);
}
