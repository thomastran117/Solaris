package backend.integration.products;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.Company;
import backend.models.core.Product;
import backend.models.core.User;
import backend.models.enums.ProductStatus;
import backend.models.enums.UserRole;
import backend.models.enums.UserStatus;
import backend.repositories.CompanyRepository;
import backend.repositories.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProductQAIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private ProductRepository productRepository;


    // ── Helpers ───────────────────────────────────────────────────────────────

    private Company createCompany(User owner) {
        Company c = new Company();
        c.setOwner(owner);
        c.setName("QA Test Company " + UUID.randomUUID());
        return companyRepository.save(c);
    }

    private Product createProduct(Company company, String name) {
        Product p = new Product();
        p.setCompany(company);
        p.setName(name);
        p.setPrice(BigDecimal.valueOf(10));
        p.setStatus(ProductStatus.ACTIVE);
        return productRepository.save(p);
    }

    private User createAdminUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("Password1!"));
        user.setRole(UserRole.ADMIN);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }

    private String askQuestionAndGetId(Product product, User user) throws Exception {
        Map<String, Object> body = Map.of("questionText", "Is this a good product?");
        return objectMapper.readTree(
                mockMvc.perform(post("/products/{productId}/questions", product.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(body))
                                .header("Authorization", bearer(accessTokenFor(user)))
                                .header("User-Agent", TEST_USER_AGENT))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString()
        ).path("data").path("id").asText();
    }

    // ── GET /products/{productId}/questions ───────────────────────────────────

    @Test
    void listQuestions_returnsEmptyPageWhenNone() throws Exception {
        User owner = createActiveUser("qa-list-empty@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "QA Product");

        mockMvc.perform(get("/products/{productId}/questions", product.getId())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void listQuestions_returns404ForUnknownProduct() throws Exception {
        mockMvc.perform(get("/products/{productId}/questions", UUID.randomUUID())
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    // ── POST /products/{productId}/questions ──────────────────────────────────

    @Test
    void askQuestion_returns201WithCreatedQuestion() throws Exception {
        User owner = createActiveUser("qa-ask-owner@example.com", "Password1!");
        User asker = createActiveUser("qa-ask@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "Ask Me Product");

        Map<String, Object> body = Map.of("questionText", "Does this come in blue?");

        mockMvc.perform(post("/products/{productId}/questions", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(asker)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.questionText").value("Does this come in blue?"));

        Integer questionRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_questions", Integer.class);
        assertEquals(1, questionRows, "Question should be persisted");
    }

    @Test
    void askQuestion_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("qa-ask-unauth@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "Unauth Ask");

        Map<String, Object> body = Map.of("questionText", "Is this available?");

        mockMvc.perform(post("/products/{productId}/questions", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void askQuestion_returns400WhenQuestionTextMissing() throws Exception {
        User user = createActiveUser("qa-ask-notext@example.com", "Password1!");
        User owner = createActiveUser("qa-ask-notext-owner@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "No Text Product");

        mockMvc.perform(post("/products/{productId}/questions", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void askQuestion_returns404ForUnknownProduct() throws Exception {
        User user = createActiveUser("qa-ask-404@example.com", "Password1!");

        Map<String, Object> body = Map.of("questionText", "Where is this?");

        mockMvc.perform(post("/products/{productId}/questions", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    // ── POST /questions/{questionId}/answers ──────────────────────────────────

    @Test
    void submitAnswer_returns201WithCreatedAnswer() throws Exception {
        User owner = createActiveUser("qa-answer-owner@example.com", "Password1!");
        User asker = createActiveUser("qa-answer-asker@example.com", "Password1!");
        User answerer = createActiveUser("qa-answer@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "Answer Me Product");
        String questionId = askQuestionAndGetId(product, asker);

        Map<String, Object> body = Map.of("answerText", "Yes, it comes in blue and red.");

        mockMvc.perform(post("/questions/{questionId}/answers", questionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(answerer)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.answerText").value("Yes, it comes in blue and red."));

        Integer answerRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_answers", Integer.class);
        assertEquals(1, answerRows, "Answer should be persisted");
    }

    @Test
    void submitAnswer_returns401WhenUnauthenticated() throws Exception {
        User owner = createActiveUser("qa-ans-unauth@example.com", "Password1!");
        User asker = createActiveUser("qa-ans-unauth-asker@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "Unauth Answer");
        String questionId = askQuestionAndGetId(product, asker);

        mockMvc.perform(post("/questions/{questionId}/answers", questionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answerText\":\"Yes\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void submitAnswer_returns400WhenAnswerTextMissing() throws Exception {
        User owner = createActiveUser("qa-ans-notext-owner@example.com", "Password1!");
        User asker = createActiveUser("qa-ans-notext-asker@example.com", "Password1!");
        User answerer = createActiveUser("qa-ans-notext@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "No Answer Text");
        String questionId = askQuestionAndGetId(product, asker);

        mockMvc.perform(post("/questions/{questionId}/answers", questionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", bearer(accessTokenFor(answerer)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitAnswer_returns404ForUnknownQuestion() throws Exception {
        User user = createActiveUser("qa-ans-404@example.com", "Password1!");

        Map<String, Object> body = Map.of("answerText", "This is an answer.");

        mockMvc.perform(post("/questions/{questionId}/answers", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    // ── POST /questions/{questionId}/answers/{answerId}/upvote ────────────────

    @Test
    void upvoteAnswer_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(post("/questions/{questionId}/answers/{answerId}/upvote",
                        UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void upvoteAnswer_returns404ForUnknownAnswer() throws Exception {
        User user = createActiveUser("qa-upvote-404@example.com", "Password1!");

        mockMvc.perform(post("/questions/{questionId}/answers/{answerId}/upvote",
                        UUID.randomUUID(), UUID.randomUUID())
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    // ── POST /questions/{questionId}/report ───────────────────────────────────

    @Test
    void reportQuestion_returns204ForAuthenticatedUser() throws Exception {
        User owner = createActiveUser("qa-report-q-owner@example.com", "Password1!");
        User reporter = createActiveUser("qa-report-q@example.com", "Password1!");
        User asker = createActiveUser("qa-report-q-asker@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "Reported Question Product");
        String questionId = askQuestionAndGetId(product, asker);

        Map<String, Object> body = Map.of("reason", "This question is inappropriate.");

        mockMvc.perform(post("/questions/{questionId}/report", questionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(reporter)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNoContent());

        Integer reportRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_question_reports", Integer.class);
        assertEquals(1, reportRows, "Q&A report should be persisted");
    }

    @Test
    void reportQuestion_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(post("/questions/{questionId}/report", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Spam\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reportQuestion_returns400WhenReasonMissing() throws Exception {
        User user = createActiveUser("qa-report-q-notext@example.com", "Password1!");
        User owner = createActiveUser("qa-report-q-notext-owner@example.com", "Password1!");
        User asker = createActiveUser("qa-report-q-notext-asker@example.com", "Password1!");
        Company company = createCompany(owner);
        Product product = createProduct(company, "Report No Reason Product");
        String questionId = askQuestionAndGetId(product, asker);

        mockMvc.perform(post("/questions/{questionId}/report", questionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    // ── POST /answers/{answerId}/report ───────────────────────────────────────

    @Test
    void reportAnswer_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(post("/answers/{answerId}/report", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Spam\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reportAnswer_returns404ForUnknownAnswer() throws Exception {
        User user = createActiveUser("qa-report-a-404@example.com", "Password1!");

        Map<String, Object> body = Map.of("reason", "Offensive content");

        mockMvc.perform(post("/answers/{answerId}/report", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }

    // ── PATCH /admin/qa/{type}/{id}/moderate ──────────────────────────────────

    @Test
    void moderate_returns401WhenUnauthenticated() throws Exception {
        mockMvc.perform(patch("/admin/qa/QUESTION/{id}/moderate", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVE\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void moderate_returns403ForNonAdmin() throws Exception {
        User user = createActiveUser("qa-mod-403@example.com", "Password1!");

        mockMvc.perform(patch("/admin/qa/QUESTION/{id}/moderate", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVE\"}")
                        .header("Authorization", bearer(accessTokenFor(user)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void moderate_returns400ForInvalidType() throws Exception {
        User admin = createAdminUser("qa-mod-badtype@example.com");

        mockMvc.perform(patch("/admin/qa/INVALID/{id}/moderate", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVE\"}")
                        .header("Authorization", bearer(accessTokenFor(admin)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void moderate_returns400ForInvalidAction() throws Exception {
        User admin = createAdminUser("qa-mod-badaction@example.com");

        mockMvc.perform(patch("/admin/qa/QUESTION/{id}/moderate", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"BANANA\"}")
                        .header("Authorization", bearer(accessTokenFor(admin)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void moderate_returns404ForUnknownContent() throws Exception {
        User admin = createAdminUser("qa-mod-404@example.com");

        mockMvc.perform(patch("/admin/qa/QUESTION/{id}/moderate", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVE\"}")
                        .header("Authorization", bearer(accessTokenFor(admin)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isNotFound());
    }
}
