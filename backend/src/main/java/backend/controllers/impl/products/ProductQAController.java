package backend.controllers.impl.products;

import backend.annotations.requireAuth.RequireAuth;
import backend.dtos.requests.qa.AskQuestionRequest;
import backend.dtos.requests.qa.ModerateQARequest;
import backend.dtos.requests.qa.ReportQAContentRequest;
import backend.dtos.requests.qa.SubmitAnswerRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.qa.AnswerResponse;
import backend.dtos.responses.qa.QuestionResponse;
import backend.exceptions.http.AppHttpException;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.InternalServerErrorException;
import backend.models.enums.QAReportType;
import backend.services.intf.RateLimitService;
import backend.services.intf.products.ProductQAModerationService;
import backend.services.intf.products.ProductQAService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Validated
@RestController
public class ProductQAController {

    private static final int QUESTION_LIMIT = 5;
    private static final int QUESTION_WINDOW_SECONDS = 3600;
    private static final int ANSWER_LIMIT = 20;
    private static final int ANSWER_WINDOW_SECONDS = 3600;

    private final ProductQAService qaService;
    private final ProductQAModerationService moderationService;
    private final RateLimitService rateLimitService;

    public ProductQAController(
            ProductQAService qaService,
            ProductQAModerationService moderationService,
            RateLimitService rateLimitService) {
        this.qaService = qaService;
        this.moderationService = moderationService;
        this.rateLimitService = rateLimitService;
    }

    @GetMapping("/products/{productId}/questions")
    public ResponseEntity<PagedResponse<QuestionResponse>> listQuestions(
            @PathVariable UUID productId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        try {
            return ResponseEntity.ok(qaService.getQuestionsForProduct(productId, page, size));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/products/{productId}/questions")
    @RequireAuth
    public ResponseEntity<QuestionResponse> askQuestion(
            @PathVariable UUID productId,
            @Valid @RequestBody AskQuestionRequest request) {
        try {
            UUID userId = resolveUserId();
            rateLimitService.enforce("qa:question", userId.toString(), QUESTION_LIMIT, QUESTION_WINDOW_SECONDS);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(qaService.askQuestion(productId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/questions/{questionId}/answers")
    @RequireAuth
    public ResponseEntity<AnswerResponse> submitAnswer(
            @PathVariable UUID questionId,
            @Valid @RequestBody SubmitAnswerRequest request) {
        try {
            UUID userId = resolveUserId();
            rateLimitService.enforce("qa:answer", userId.toString(), ANSWER_LIMIT, ANSWER_WINDOW_SECONDS);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(qaService.answerQuestion(questionId, userId, request));
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/questions/{questionId}/answers/{answerId}/upvote")
    @RequireAuth
    public ResponseEntity<Void> upvoteAnswer(
            @PathVariable UUID questionId,
            @PathVariable UUID answerId) {
        try {
            UUID userId = resolveUserId();
            qaService.upvoteAnswer(questionId, answerId, userId);
            return ResponseEntity.noContent().build();
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/questions/{questionId}/report")
    @RequireAuth
    public ResponseEntity<Void> reportQuestion(
            @PathVariable UUID questionId,
            @Valid @RequestBody ReportQAContentRequest request) {
        try {
            UUID userId = resolveUserId();
            qaService.reportContent(QAReportType.QUESTION, questionId, userId, request);
            return ResponseEntity.noContent().build();
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PostMapping("/answers/{answerId}/report")
    @RequireAuth
    public ResponseEntity<Void> reportAnswer(
            @PathVariable UUID answerId,
            @Valid @RequestBody ReportQAContentRequest request) {
        try {
            UUID userId = resolveUserId();
            qaService.reportContent(QAReportType.ANSWER, answerId, userId, request);
            return ResponseEntity.noContent().build();
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    @PatchMapping("/admin/qa/{type}/{id}/moderate")
    @RequireAuth(roles = {"ADMIN"})
    public ResponseEntity<Void> moderateContent(
            @PathVariable String type,
            @PathVariable UUID id,
            @Valid @RequestBody ModerateQARequest request) {
        try {
            QAReportType reportType = parseType(type);
            moderationService.moderateContent(reportType, id, request);
            return ResponseEntity.noContent().build();
        } catch (AppHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalServerErrorException();
        }
    }

    private QAReportType parseType(String type) {
        try {
            return QAReportType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid type: must be QUESTION or ANSWER");
        }
    }

    private UUID resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (UUID) auth.getPrincipal();
    }
}
