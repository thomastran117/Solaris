package backend.integration.b2b;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.B2BAccount;
import backend.models.core.B2BQuote;
import backend.models.core.B2BQuoteItem;
import backend.models.core.Company;
import backend.models.core.CompanyMembership;
import backend.models.core.Product;
import backend.models.core.User;
import backend.models.enums.CompanyMembershipStatus;
import backend.models.enums.CompanyRole;
import backend.models.enums.CompanyStatus;
import backend.models.enums.PaymentTerms;
import backend.models.enums.ProductStatus;
import backend.models.enums.QuoteStatus;
import backend.repositories.B2BAccountRepository;
import backend.repositories.B2BInvoiceRepository;
import backend.repositories.B2BQuoteRepository;
import backend.repositories.CompanyMembershipRepository;
import backend.repositories.CompanyRepository;
import backend.repositories.ProductRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class B2BQuoteIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private CompanyMembershipRepository membershipRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private B2BQuoteRepository quoteRepository;
    @Autowired private B2BInvoiceRepository invoiceRepository;
    @Autowired private B2BAccountRepository accountRepository;
    @Autowired private backend.repositories.OrderRepository orderRepository;


    private Company createCompany(User owner, String name) {
        Company c = new Company();
        c.setOwner(owner);
        c.setName(name);
        c.setStatus(CompanyStatus.ACTIVE);
        return companyRepository.save(c);
    }

    private void addMember(User user, Company company, CompanyRole role) {
        CompanyMembership m = new CompanyMembership();
        m.setCompany(company);
        m.setUser(user);
        m.setRole(role);
        m.setStatus(CompanyMembershipStatus.ACTIVE);
        membershipRepository.save(m);
    }

    private Product createProduct(Company company, int stock) {
        Product p = new Product();
        p.setCompany(company);
        p.setName("Wholesale Widget");
        p.setSku("SKU-" + UUID.randomUUID().toString().substring(0, 8));
        p.setPrice(BigDecimal.valueOf(50.00));
        p.setStatus(ProductStatus.ACTIVE);
        p.setStock(stock);
        return productRepository.save(p);
    }

    private Map<String, Object> quoteBody(UUID productId, int qty, String terms) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("productId", productId.toString());
        item.put("quantity", qty);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("companyName", "Buyer Corp");
        body.put("taxId", "TAX-9");
        body.put("billingAddress", "1 Market St");
        body.put("message", "Bulk order please");
        body.put("paymentTerms", terms);
        body.put("items", List.of(item));
        return body;
    }

    @Test
    void requestQuote_returns201AndPendingVendor() throws Exception {
        User buyer = createActiveUser("b2b-buyer@example.com", "Password1!");
        User vendorOwner = createActiveUser("b2b-vendor@example.com", "Password1!");
        Company vendor = createCompany(vendorOwner, "Vendor Co");
        addMember(vendorOwner, vendor, CompanyRole.OWNER);
        Product product = createProduct(vendor, 100);

        mockMvc.perform(post("/b2b/quotes")
                        .param("vendorCompanyId", vendor.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(quoteBody(product.getId(), 4, "NET_30")))
                        .header("Authorization", bearer(accessTokenFor(buyer)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING_VENDOR"))
                .andExpect(jsonPath("$.data.items[0].unitPriceCents").value(5000))
                .andExpect(jsonPath("$.data.totalCents").value(20000));

        // Buyer B2BAccount auto-created.
        org.junit.jupiter.api.Assertions.assertTrue(accountRepository.findByUserId(buyer.getId()).isPresent());

        // The quote itself must be persisted in PENDING_VENDOR.
        org.junit.jupiter.api.Assertions.assertEquals(1, quoteRepository.count());
        org.junit.jupiter.api.Assertions.assertEquals(QuoteStatus.PENDING_VENDOR,
                quoteRepository.findAll().get(0).getStatus());
    }

    @Test
    void vendorRespondApprove_movesToPendingBuyer() throws Exception {
        User buyer = createActiveUser("b2b-buyer2@example.com", "Password1!");
        User vendorOwner = createActiveUser("b2b-vendor2@example.com", "Password1!");
        Company vendor = createCompany(vendorOwner, "Vendor Co2");
        addMember(vendorOwner, vendor, CompanyRole.OWNER);
        Product product = createProduct(vendor, 100);
        B2BQuote quote = seedQuote(buyer, vendor, product, QuoteStatus.PENDING_VENDOR, PaymentTerms.NET_30,
                Instant.now().plus(7, ChronoUnit.DAYS));

        Map<String, Object> body = Map.of("action", "APPROVE", "vendorNote", "Approved");

        mockMvc.perform(patch("/companies/{cid}/b2b/quotes/{id}/respond", vendor.getId(), quote.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(vendorOwner)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_BUYER"));

        org.junit.jupiter.api.Assertions.assertEquals(QuoteStatus.PENDING_BUYER,
                quoteRepository.findById(quote.getId()).orElseThrow().getStatus());
    }

    @Test
    void vendorRespond_forbiddenForNonMember() throws Exception {
        User buyer = createActiveUser("b2b-buyer3@example.com", "Password1!");
        User vendorOwner = createActiveUser("b2b-vendor3@example.com", "Password1!");
        User stranger = createActiveUser("b2b-stranger@example.com", "Password1!");
        Company vendor = createCompany(vendorOwner, "Vendor Co3");
        addMember(vendorOwner, vendor, CompanyRole.OWNER);
        Product product = createProduct(vendor, 100);
        B2BQuote quote = seedQuote(buyer, vendor, product, QuoteStatus.PENDING_VENDOR, PaymentTerms.NET_30,
                Instant.now().plus(7, ChronoUnit.DAYS));

        Map<String, Object> body = Map.of("action", "APPROVE");

        mockMvc.perform(patch("/companies/{cid}/b2b/quotes/{id}/respond", vendor.getId(), quote.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", bearer(accessTokenFor(stranger)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isForbidden());
    }

    @Test
    void acceptExpiredQuote_returns409() throws Exception {
        User buyer = createActiveUser("b2b-buyer4@example.com", "Password1!");
        User vendorOwner = createActiveUser("b2b-vendor4@example.com", "Password1!");
        Company vendor = createCompany(vendorOwner, "Vendor Co4");
        addMember(vendorOwner, vendor, CompanyRole.OWNER);
        Product product = createProduct(vendor, 100);
        approveNetTerms(buyer, 1_000_000);
        B2BQuote quote = seedQuote(buyer, vendor, product, QuoteStatus.PENDING_BUYER, PaymentTerms.NET_30,
                Instant.now().minus(1, ChronoUnit.HOURS));

        mockMvc.perform(post("/b2b/quotes/{id}/accept", quote.getId())
                        .header("Authorization", bearer(accessTokenFor(buyer)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isConflict());
    }

    @Test
    void acceptNetTermsQuote_createsOrderAndInvoice() throws Exception {
        User buyer = createActiveUser("b2b-buyer5@example.com", "Password1!");
        User vendorOwner = createActiveUser("b2b-vendor5@example.com", "Password1!");
        Company vendor = createCompany(vendorOwner, "Vendor Co5");
        addMember(vendorOwner, vendor, CompanyRole.OWNER);
        Product product = createProduct(vendor, 100);
        approveNetTerms(buyer, 1_000_000);
        B2BQuote quote = seedQuote(buyer, vendor, product, QuoteStatus.PENDING_BUYER, PaymentTerms.NET_30,
                Instant.now().plus(7, ChronoUnit.DAYS));

        String resp = mockMvc.perform(post("/b2b/quotes/{id}/accept", quote.getId())
                        .header("Authorization", bearer(accessTokenFor(buyer)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode node = objectMapper.readTree(resp);
        String orderId = node.path("data").path("id").asText();
        org.junit.jupiter.api.Assertions.assertFalse(orderId.isBlank());

        // Quote converted, invoice issued for the order.
        B2BQuote reloaded = quoteRepository.findById(quote.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(QuoteStatus.CONVERTED, reloaded.getStatus());
        org.junit.jupiter.api.Assertions.assertTrue(
                invoiceRepository.findByOrderId(UUID.fromString(orderId)).isPresent());
    }

    @Test
    void acceptNetTermsQuote_isIdempotentOnRetry() throws Exception {
        User buyer = createActiveUser("b2b-buyer6@example.com", "Password1!");
        User vendorOwner = createActiveUser("b2b-vendor6@example.com", "Password1!");
        Company vendor = createCompany(vendorOwner, "Vendor Co6");
        addMember(vendorOwner, vendor, CompanyRole.OWNER);
        Product product = createProduct(vendor, 100);
        approveNetTerms(buyer, 1_000_000);
        B2BQuote quote = seedQuote(buyer, vendor, product, QuoteStatus.PENDING_BUYER, PaymentTerms.NET_30,
                Instant.now().plus(7, ChronoUnit.DAYS));

        String firstOrderId = acceptAndGetOrderId(quote.getId(), buyer);
        String secondOrderId = acceptAndGetOrderId(quote.getId(), buyer);

        org.junit.jupiter.api.Assertions.assertEquals(firstOrderId, secondOrderId,
                "Re-accepting a converted quote must return the same order");
        // Exactly one order references this quote.
        org.junit.jupiter.api.Assertions.assertEquals(
                firstOrderId,
                orderRepository.findByB2bQuoteId(quote.getId()).orElseThrow().getId().toString());
    }

    private String acceptAndGetOrderId(UUID quoteId, User buyer) throws Exception {
        String resp = mockMvc.perform(post("/b2b/quotes/{id}/accept", quoteId)
                        .header("Authorization", bearer(accessTokenFor(buyer)))
                        .header("User-Agent", TEST_USER_AGENT))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").path("id").asText();
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private void approveNetTerms(User buyer, long limitCents) {
        B2BAccount account = new B2BAccount();
        account.setUserId(buyer.getId());
        account.setCompanyName("Buyer Corp");
        account.setNetTermsApproved(true);
        account.setNetTermsLimitCents(limitCents);
        accountRepository.save(account);
    }

    private B2BQuote seedQuote(User buyer, Company vendor, Product product, QuoteStatus status,
                               PaymentTerms terms, Instant expiresAt) {
        B2BQuote quote = new B2BQuote();
        quote.setVendorCompanyId(vendor.getId());
        quote.setBuyerUserId(buyer.getId());
        quote.setStatus(status);
        quote.setPaymentTerms(terms);
        quote.setExpiresAt(expiresAt);
        B2BQuoteItem item = new B2BQuoteItem();
        item.setQuote(quote);
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setQuantity(2);
        item.setUnitPriceCents(5000);
        item.recomputeTotal();
        quote.getItems().add(item);
        return quoteRepository.save(quote);
    }
}
