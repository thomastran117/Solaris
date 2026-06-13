package backend.services.impl.products;

import backend.dtos.requests.qa.AskQuestionRequest;
import backend.dtos.requests.qa.ReportQAContentRequest;
import backend.dtos.requests.qa.SubmitAnswerRequest;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.qa.AnswerResponse;
import backend.dtos.responses.qa.QuestionResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.Product;
import backend.models.core.ProductAnswer;
import backend.models.core.ProductAnswerUpvote;
import backend.models.core.ProductQuestion;
import backend.models.core.ProductQuestionReport;
import backend.models.core.User;
import backend.models.enums.CompanyMembershipStatus;
import backend.models.enums.ProductStatus;
import backend.models.enums.QAReportType;
import backend.models.enums.QAStatus;
import backend.repositories.CompanyMembershipRepository;
import backend.repositories.ProductAnswerRepository;
import backend.repositories.ProductAnswerUpvoteRepository;
import backend.repositories.ProductQuestionReportRepository;
import backend.repositories.ProductQuestionRepository;
import backend.repositories.ProductRepository;
import backend.repositories.UserRepository;
import backend.services.intf.products.ProductQAService;
import backend.services.intf.support.EmailService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

@Service
public class ProductQAServiceImpl implements ProductQAService {

    private static final int REPORT_THRESHOLD = 5;

    private final ProductQuestionRepository questionRepository;
    private final ProductAnswerRepository answerRepository;
    private final ProductAnswerUpvoteRepository upvoteRepository;
    private final ProductQuestionReportRepository reportRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CompanyMembershipRepository membershipRepository;
    private final EmailService emailService;

    public ProductQAServiceImpl(
            ProductQuestionRepository questionRepository,
            ProductAnswerRepository answerRepository,
            ProductAnswerUpvoteRepository upvoteRepository,
            ProductQuestionReportRepository reportRepository,
            ProductRepository productRepository,
            UserRepository userRepository,
            CompanyMembershipRepository membershipRepository,
            EmailService emailService) {
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.upvoteRepository = upvoteRepository;
        this.reportRepository = reportRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.emailService = emailService;
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<QuestionResponse> getQuestionsForProduct(UUID productId, int page, int size) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));
        // Q&A is a public storefront surface — only expose it for publicly visible (ACTIVE + listed)
        // products so hidden/draft/unlisted products don't leak or accrue public Q&A.
        if (product.getStatus() != ProductStatus.ACTIVE || !product.isListed()) {
            throw new ResourceNotFoundException("Product not found with id: " + productId);
        }

        int clampedSize = Math.min(size, 50);
        Page<ProductQuestion> questionPage = questionRepository.findByProductIdAndStatus(
                productId, QAStatus.VISIBLE,
                PageRequest.of(page, clampedSize, Sort.by(Sort.Direction.DESC, "createdAt")));

        return new PagedResponse<>(questionPage.map(q -> {
            List<ProductAnswer> answers = answerRepository
                    .findByQuestionIdAndStatusOrderByUpvoteCountDesc(q.getId(), QAStatus.VISIBLE);
            return toQuestionResponse(q, answers.stream().map(this::toAnswerResponse).toList());
        }));
    }

    @Override
    @Transactional
    public QuestionResponse askQuestion(UUID productId, UUID userId, AskQuestionRequest request) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));
        if (product.getStatus() != ProductStatus.ACTIVE || !product.isListed()) {
            throw new BadRequestException("Questions can only be submitted on publicly listed, active products");
        }
        User asker = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        ProductQuestion question = new ProductQuestion();
        question.setProduct(product);
        question.setAskedBy(asker);
        question.setQuestionText(request.getQuestionText());
        question.setStatus(QAStatus.VISIBLE);
        ProductQuestion saved = questionRepository.save(question);

        notifyVendorAfterCommit(product, asker, saved);

        return toQuestionResponse(saved, List.of());
    }

    @Override
    @Transactional
    public AnswerResponse answerQuestion(UUID questionId, UUID userId, SubmitAnswerRequest request) {
        ProductQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found with id: " + questionId));
        if (question.getStatus() != QAStatus.VISIBLE) {
            throw new BadRequestException("This question is not available for answering");
        }
        User answerer = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        UUID companyId = question.getProduct().getCompany().getId();
        boolean isVendor = membershipRepository.existsByCompanyIdAndUserIdAndStatus(
                companyId, userId, CompanyMembershipStatus.ACTIVE);

        ProductAnswer answer = new ProductAnswer();
        answer.setQuestion(question);
        answer.setAnsweredBy(answerer);
        answer.setAnswerText(request.getAnswerText());
        answer.setVendorAnswer(isVendor);
        answer.setStatus(QAStatus.VISIBLE);
        ProductAnswer saved = answerRepository.save(answer);

        return toAnswerResponse(saved);
    }

    @Override
    @Transactional
    public void upvoteAnswer(UUID questionId, UUID answerId, UUID userId) {
        ProductAnswer answer = answerRepository.findById(answerId)
                .orElseThrow(() -> new ResourceNotFoundException("Answer not found with id: " + answerId));
        // Verify the answer actually belongs to the question in the request path — otherwise the
        // route /questions/{questionId}/answers/{answerId}/upvote is only cosmetically scoped.
        if (answer.getQuestion() == null || !answer.getQuestion().getId().equals(questionId)) {
            throw new ResourceNotFoundException(
                    "Answer " + answerId + " not found under question " + questionId);
        }
        if (answer.getStatus() != QAStatus.VISIBLE) {
            throw new BadRequestException("This answer is not available for upvoting");
        }
        if (upvoteRepository.existsByAnswerIdAndUserId(answerId, userId)) {
            throw new ConflictException("You have already upvoted this answer");
        }

        ProductAnswerUpvote upvote = new ProductAnswerUpvote();
        upvote.setAnswerId(answerId);
        upvote.setUserId(userId);
        upvoteRepository.save(upvote);
        answerRepository.incrementUpvoteCount(answerId);
    }

    @Override
    @Transactional
    public void reportContent(QAReportType type, UUID targetId, UUID userId, ReportQAContentRequest request) {
        User reporter = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        if (type == QAReportType.QUESTION) {
            ProductQuestion question = questionRepository.findById(targetId)
                    .orElseThrow(() -> new ResourceNotFoundException("Question not found with id: " + targetId));
            saveReport(type, targetId, reporter, request.getReason());
            int newCount = question.getReportCount() + 1;
            questionRepository.incrementReportCount(targetId);
            if (newCount >= REPORT_THRESHOLD) {
                questionRepository.updateStatusIfDifferent(targetId, QAStatus.PENDING_MODERATION.name());
            }
        } else {
            ProductAnswer answer = answerRepository.findById(targetId)
                    .orElseThrow(() -> new ResourceNotFoundException("Answer not found with id: " + targetId));
            saveReport(type, targetId, reporter, request.getReason());
            int newCount = answer.getReportCount() + 1;
            answerRepository.incrementReportCount(targetId);
            if (newCount >= REPORT_THRESHOLD) {
                answerRepository.updateStatusIfDifferent(targetId, QAStatus.PENDING_MODERATION.name());
            }
        }
    }

    private void saveReport(QAReportType type, UUID targetId, User reporter, String reason) {
        ProductQuestionReport report = new ProductQuestionReport();
        report.setTargetType(type);
        report.setTargetId(targetId);
        report.setReportedBy(reporter);
        report.setReason(reason);
        reportRepository.save(report);
    }

    private void notifyVendorAfterCommit(Product product, User asker, ProductQuestion saved) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        User companyOwner = product.getCompany().getOwner();
                        if (companyOwner != null && companyOwner.getEmail() != null) {
                            emailService.sendQuestionPostedEmail(
                                    companyOwner.getId(),
                                    companyOwner.getEmail(),
                                    companyOwner.getFirstName(),
                                    product.getName(),
                                    saved.getQuestionText(),
                                    saved.getId());
                        }
                    } catch (Exception e) {
                        // Non-critical — don't let email failure bubble up
                    }
                }
            });
        }
    }

    private QuestionResponse toQuestionResponse(ProductQuestion q, List<AnswerResponse> answers) {
        return new QuestionResponse(
                q.getId(),
                q.getProduct().getId(),
                q.getAskedBy().getId(),
                q.getAskedBy().getFirstName(),
                q.getAskedBy().getLastName(),
                q.getQuestionText(),
                q.getStatus().name(),
                answers,
                q.getCreatedAt(),
                q.getUpdatedAt());
    }

    private AnswerResponse toAnswerResponse(ProductAnswer a) {
        return new AnswerResponse(
                a.getId(),
                a.getQuestion().getId(),
                a.getAnsweredBy().getId(),
                a.getAnsweredBy().getFirstName(),
                a.getAnsweredBy().getLastName(),
                a.getAnswerText(),
                a.isVendorAnswer(),
                a.getUpvoteCount(),
                a.getStatus().name(),
                a.getCreatedAt(),
                a.getUpdatedAt());
    }
}
