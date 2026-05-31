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
import backend.models.core.Company;
import backend.models.core.Product;
import backend.models.core.ProductAnswer;
import backend.models.core.ProductAnswerUpvote;
import backend.models.core.ProductQuestion;
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
import backend.services.intf.support.EmailService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductQAServiceImplTest {

    private static final UUID PRODUCT_ID  = TestIds.uuid(1);
    private static final UUID COMPANY_ID  = TestIds.uuid(2);
    private static final UUID USER_ID     = TestIds.uuid(3);
    private static final UUID QUESTION_ID = TestIds.uuid(4);
    private static final UUID ANSWER_ID   = TestIds.uuid(5);
    private static final UUID VENDOR_ID   = TestIds.uuid(6);

    private ProductQuestionRepository questionRepository;
    private ProductAnswerRepository answerRepository;
    private ProductAnswerUpvoteRepository upvoteRepository;
    private ProductQuestionReportRepository reportRepository;
    private ProductRepository productRepository;
    private UserRepository userRepository;
    private CompanyMembershipRepository membershipRepository;
    private EmailService emailService;

    private ProductQAServiceImpl service;

    @BeforeEach
    void setUp() {
        questionRepository  = mock(ProductQuestionRepository.class);
        answerRepository    = mock(ProductAnswerRepository.class);
        upvoteRepository    = mock(ProductAnswerUpvoteRepository.class);
        reportRepository    = mock(ProductQuestionReportRepository.class);
        productRepository   = mock(ProductRepository.class);
        userRepository      = mock(UserRepository.class);
        membershipRepository = mock(CompanyMembershipRepository.class);
        emailService        = mock(EmailService.class);

        service = new ProductQAServiceImpl(
                questionRepository, answerRepository, upvoteRepository,
                reportRepository, productRepository, userRepository,
                membershipRepository, emailService);

        // Common stubs
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(makeProduct()));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(makeUser(USER_ID, "Alice", "Smith")));
        when(questionRepository.save(any(ProductQuestion.class))).thenAnswer(inv -> {
            ProductQuestion q = inv.getArgument(0);
            if (q.getId() == null) q.setId(QUESTION_ID);
            return q;
        });
        when(answerRepository.save(any(ProductAnswer.class))).thenAnswer(inv -> {
            ProductAnswer a = inv.getArgument(0);
            if (a.getId() == null) a.setId(ANSWER_ID);
            return a;
        });
    }

    // ─── askQuestion ──────────────────────────────────────────────────────────

    @Test
    void askQuestion_happyPath_savesAndReturnsResponse() {
        QuestionResponse result = service.askQuestion(PRODUCT_ID, USER_ID, makeAskRequest("Is this waterproof?"));

        assertNotNull(result);
        assertEquals("Is this waterproof?", result.getQuestionText());
        assertEquals("VISIBLE", result.getStatus());
        verify(questionRepository).save(any(ProductQuestion.class));
    }

    @Test
    void askQuestion_productNotFound_throwsResourceNotFound() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.askQuestion(PRODUCT_ID, USER_ID, makeAskRequest("test?")));
    }

    @Test
    void askQuestion_productNotActive_throwsBadRequest() {
        Product p = makeProduct();
        p.setStatus(ProductStatus.DRAFT);
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(p));

        assertThrows(BadRequestException.class,
                () -> service.askQuestion(PRODUCT_ID, USER_ID, makeAskRequest("test?")));
    }

    @Test
    void askQuestion_userNotFound_throwsResourceNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.askQuestion(PRODUCT_ID, USER_ID, makeAskRequest("test?")));
    }

    // ─── answerQuestion ───────────────────────────────────────────────────────

    @Test
    void answerQuestion_happyPath_regularUser_isVendorAnswerFalse() {
        when(questionRepository.findById(QUESTION_ID)).thenReturn(Optional.of(makeQuestion()));
        when(membershipRepository.existsByCompanyIdAndUserIdAndStatus(
                COMPANY_ID, USER_ID, CompanyMembershipStatus.ACTIVE)).thenReturn(false);

        AnswerResponse result = service.answerQuestion(QUESTION_ID, USER_ID, makeAnswerRequest("Great question!"));

        assertNotNull(result);
        assertFalse(result.isVendorAnswer());
        verify(answerRepository).save(any(ProductAnswer.class));
    }

    @Test
    void answerQuestion_vendorUser_isVendorAnswerTrue() {
        when(questionRepository.findById(QUESTION_ID)).thenReturn(Optional.of(makeQuestion()));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(makeUser(USER_ID, "Vendor", "User")));
        when(membershipRepository.existsByCompanyIdAndUserIdAndStatus(
                COMPANY_ID, USER_ID, CompanyMembershipStatus.ACTIVE)).thenReturn(true);

        AnswerResponse result = service.answerQuestion(QUESTION_ID, USER_ID, makeAnswerRequest("Official answer."));

        assertTrue(result.isVendorAnswer());
    }

    @Test
    void answerQuestion_questionNotFound_throwsResourceNotFound() {
        when(questionRepository.findById(QUESTION_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.answerQuestion(QUESTION_ID, USER_ID, makeAnswerRequest("answer")));
    }

    @Test
    void answerQuestion_questionNotVisible_throwsBadRequest() {
        ProductQuestion q = makeQuestion();
        q.setStatus(QAStatus.PENDING_MODERATION);
        when(questionRepository.findById(QUESTION_ID)).thenReturn(Optional.of(q));

        assertThrows(BadRequestException.class,
                () -> service.answerQuestion(QUESTION_ID, USER_ID, makeAnswerRequest("answer")));
    }

    @Test
    void answerQuestion_userNotFound_throwsResourceNotFound() {
        when(questionRepository.findById(QUESTION_ID)).thenReturn(Optional.of(makeQuestion()));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.answerQuestion(QUESTION_ID, USER_ID, makeAnswerRequest("answer")));
    }

    // ─── upvoteAnswer ─────────────────────────────────────────────────────────

    @Test
    void upvoteAnswer_happyPath_savesUpvoteAndIncrementsCount() {
        when(answerRepository.findById(ANSWER_ID)).thenReturn(Optional.of(makeAnswer()));
        when(upvoteRepository.existsByAnswerIdAndUserId(ANSWER_ID, USER_ID)).thenReturn(false);

        service.upvoteAnswer(ANSWER_ID, USER_ID);

        verify(upvoteRepository).save(any(ProductAnswerUpvote.class));
        verify(answerRepository).incrementUpvoteCount(ANSWER_ID);
    }

    @Test
    void upvoteAnswer_alreadyVoted_throwsConflict() {
        when(answerRepository.findById(ANSWER_ID)).thenReturn(Optional.of(makeAnswer()));
        when(upvoteRepository.existsByAnswerIdAndUserId(ANSWER_ID, USER_ID)).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.upvoteAnswer(ANSWER_ID, USER_ID));
        verify(upvoteRepository, never()).save(any());
    }

    @Test
    void upvoteAnswer_answerNotFound_throwsResourceNotFound() {
        when(answerRepository.findById(ANSWER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.upvoteAnswer(ANSWER_ID, USER_ID));
    }

    // ─── reportContent ────────────────────────────────────────────────────────

    @Test
    void reportContent_question_belowThreshold_remainsVisible() {
        ProductQuestion q = makeQuestion();
        q.setReportCount(2);
        when(questionRepository.findById(QUESTION_ID)).thenReturn(Optional.of(q));

        service.reportContent(QAReportType.QUESTION, QUESTION_ID, USER_ID, makeReportRequest("spam"));

        verify(questionRepository).incrementReportCount(QUESTION_ID);
        verify(questionRepository, never()).updateStatusIfDifferent(any(), any());
    }

    @Test
    void reportContent_question_atThreshold_transitionsToPendingModeration() {
        ProductQuestion q = makeQuestion();
        q.setReportCount(4); // next report (4+1=5) triggers transition
        when(questionRepository.findById(QUESTION_ID)).thenReturn(Optional.of(q));

        service.reportContent(QAReportType.QUESTION, QUESTION_ID, USER_ID, makeReportRequest("spam"));

        verify(questionRepository).incrementReportCount(QUESTION_ID);
        verify(questionRepository).updateStatusIfDifferent(QUESTION_ID, QAStatus.PENDING_MODERATION.name());
    }

    @Test
    void reportContent_answer_atThreshold_transitionsToPendingModeration() {
        ProductAnswer a = makeAnswer();
        a.setReportCount(4);
        when(answerRepository.findById(ANSWER_ID)).thenReturn(Optional.of(a));

        service.reportContent(QAReportType.ANSWER, ANSWER_ID, USER_ID, makeReportRequest("offensive"));

        verify(answerRepository).incrementReportCount(ANSWER_ID);
        verify(answerRepository).updateStatusIfDifferent(ANSWER_ID, QAStatus.PENDING_MODERATION.name());
    }

    @Test
    void reportContent_question_targetNotFound_throwsResourceNotFound() {
        when(questionRepository.findById(QUESTION_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.reportContent(QAReportType.QUESTION, QUESTION_ID, USER_ID, makeReportRequest("spam")));
    }

    @Test
    void reportContent_answer_targetNotFound_throwsResourceNotFound() {
        when(answerRepository.findById(ANSWER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.reportContent(QAReportType.ANSWER, ANSWER_ID, USER_ID, makeReportRequest("spam")));
    }

    // ─── getQuestionsForProduct ───────────────────────────────────────────────

    @Test
    void getQuestionsForProduct_happyPath_returnsPaged() {
        ProductQuestion q = makeQuestion();
        when(questionRepository.findByProductIdAndStatus(eq(PRODUCT_ID), eq(QAStatus.VISIBLE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(q)));
        when(answerRepository.findByQuestionIdAndStatusOrderByUpvoteCountDesc(QUESTION_ID, QAStatus.VISIBLE))
                .thenReturn(List.of());

        PagedResponse<QuestionResponse> result = service.getQuestionsForProduct(PRODUCT_ID, 0, 20);

        assertNotNull(result);
        assertEquals(1, result.getItems().size());
        assertEquals("Is this waterproof?", result.getItems().get(0).getQuestionText());
    }

    @Test
    void getQuestionsForProduct_productNotFound_throwsResourceNotFound() {
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getQuestionsForProduct(PRODUCT_ID, 0, 20));
    }

    @Test
    void getQuestionsForProduct_answersOrderedByUpvoteCountDesc() {
        ProductQuestion q = makeQuestion();
        ProductAnswer a1 = makeAnswerWithUpvotes(TestIds.uuid(10), 5);
        ProductAnswer a2 = makeAnswerWithUpvotes(TestIds.uuid(11), 1);
        when(questionRepository.findByProductIdAndStatus(eq(PRODUCT_ID), eq(QAStatus.VISIBLE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(q)));
        // Repository already returns sorted; verify we pass them through in order
        when(answerRepository.findByQuestionIdAndStatusOrderByUpvoteCountDesc(QUESTION_ID, QAStatus.VISIBLE))
                .thenReturn(List.of(a1, a2));

        PagedResponse<QuestionResponse> result = service.getQuestionsForProduct(PRODUCT_ID, 0, 20);

        List<AnswerResponse> answers = result.getItems().get(0).getAnswers();
        assertEquals(2, answers.size());
        assertEquals(5, answers.get(0).getUpvoteCount());
        assertEquals(1, answers.get(1).getUpvoteCount());
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private Product makeProduct() {
        User owner = makeUser(VENDOR_ID, "Vendor", "Owner");
        owner.setEmail("vendor@test.com");

        Company company = new Company();
        company.setId(COMPANY_ID);
        company.setOwner(owner);

        Product p = new Product();
        p.setId(PRODUCT_ID);
        p.setCompany(company);
        p.setName("Test Product");
        p.setStatus(ProductStatus.ACTIVE);
        return p;
    }

    private User makeUser(UUID id, String firstName, String lastName) {
        User u = new User();
        u.setId(id);
        u.setFirstName(firstName);
        u.setLastName(lastName);
        u.setEmail(firstName.toLowerCase() + "@test.com");
        return u;
    }

    private ProductQuestion makeQuestion() {
        ProductQuestion q = new ProductQuestion();
        q.setId(QUESTION_ID);
        q.setProduct(makeProduct());
        q.setAskedBy(makeUser(USER_ID, "Alice", "Smith"));
        q.setQuestionText("Is this waterproof?");
        q.setStatus(QAStatus.VISIBLE);
        q.setReportCount(0);
        return q;
    }

    private ProductAnswer makeAnswer() {
        ProductAnswer a = new ProductAnswer();
        a.setId(ANSWER_ID);
        a.setQuestion(makeQuestion());
        a.setAnsweredBy(makeUser(USER_ID, "Alice", "Smith"));
        a.setAnswerText("Yes, it is.");
        a.setVendorAnswer(false);
        a.setUpvoteCount(0);
        a.setStatus(QAStatus.VISIBLE);
        a.setReportCount(0);
        return a;
    }

    private ProductAnswer makeAnswerWithUpvotes(UUID id, int upvotes) {
        ProductAnswer a = makeAnswer();
        a.setId(id);
        a.setUpvoteCount(upvotes);
        return a;
    }

    private AskQuestionRequest makeAskRequest(String text) {
        AskQuestionRequest req = new AskQuestionRequest();
        req.setQuestionText(text);
        return req;
    }

    private SubmitAnswerRequest makeAnswerRequest(String text) {
        SubmitAnswerRequest req = new SubmitAnswerRequest();
        req.setAnswerText(text);
        return req;
    }

    private ReportQAContentRequest makeReportRequest(String reason) {
        ReportQAContentRequest req = new ReportQAContentRequest();
        req.setReason(reason);
        return req;
    }
}
