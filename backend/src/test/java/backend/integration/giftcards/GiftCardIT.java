package backend.integration.giftcards;

import backend.integration.AbstractIntegrationIT;
import backend.models.core.Company;
import backend.models.core.GiftCard;
import backend.models.core.User;
import backend.models.enums.CompanyStatus;
import backend.models.enums.GiftCardStatus;
import backend.models.enums.UserRole;
import backend.models.enums.UserStatus;
import backend.repositories.CompanyRepository;
import backend.repositories.GiftCardRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GiftCardIT extends AbstractIntegrationIT {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private GiftCardRepository giftCardRepository;


    // ── Helpers ───────────────────────────────────────────────────────────────

    private User createAdminUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("Password1!"));
        user.setRole(UserRole.ADMIN);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }

    private Company createCompany(User owner) {
        Company company = new Company();
        company.setOwner(owner);
        company.setName("GC Co " + UUID.randomUUID().toString().substring(0, 8));
        company.setStatus(CompanyStatus.ACTIVE);
        return companyRepository.save(company);
    }

    private GiftCard createCard(User purchaser, Company company, String code,
                                long valueCents, GiftCardStatus status) {
        GiftCard card = new GiftCard();
        card.setCompany(company);
        card.setCode(code);
        card.setOriginalValueCents(valueCents);
        card.setRemainingBalanceCents(valueCents);
        card.setPurchasedByUser(purchaser);
        card.setStatus(status);
        return giftCardRepository.save(card);
    }

    private String redeemBody(String code, long amountCents) throws Exception {
        return objectMapper.writeValueAsString(Map.of("code", code, "amountCents", amountCents));
    }

    // ── POST /gift-cards/redeem ───────────────────────────────────────────────

    @Test
    void redeem_fullAmount_returns200AndStatusRedeemed() throws Exception {
        User buyer = createActiveUser("gc-redeem-full@example.com", "Password1!");
        User redeemer = createActiveUser("gc-redeem-user@example.com", "Password1!");
        Company company = createCompany(buyer);
        GiftCard card = createCard(buyer, company, "ABCD1234EFGH5678", 5000L, GiftCardStatus.ACTIVE);

        mockMvc.perform(post("/gift-cards/redeem")
                        .header("Authorization", bearer(accessTokenFor(redeemer)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redeemBody("ABCD1234EFGH5678", 5000L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("ABCD1234EFGH5678"))
                .andExpect(jsonPath("$.data.remainingBalanceCents").value(0))
                .andExpect(jsonPath("$.data.status").value("REDEEMED"));

        // The debited balance and terminal status must be committed to the database.
        GiftCard persisted = giftCardRepository.findById(card.getId()).orElseThrow();
        assertEquals(0L, persisted.getRemainingBalanceCents());
        assertEquals(GiftCardStatus.REDEEMED, persisted.getStatus());
    }

    @Test
    void redeem_partialAmount_returns200AndStatusPartiallyUsed() throws Exception {
        User buyer = createActiveUser("gc-redeem-partial-buyer@example.com", "Password1!");
        User redeemer = createActiveUser("gc-redeem-partial-user@example.com", "Password1!");
        Company company = createCompany(buyer);
        GiftCard card = createCard(buyer, company, "PARTIAL123456789", 5000L, GiftCardStatus.ACTIVE);

        mockMvc.perform(post("/gift-cards/redeem")
                        .header("Authorization", bearer(accessTokenFor(redeemer)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redeemBody("PARTIAL123456789", 2000L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remainingBalanceCents").value(3000))
                .andExpect(jsonPath("$.data.status").value("PARTIALLY_USED"));

        GiftCard persisted = giftCardRepository.findById(card.getId()).orElseThrow();
        assertEquals(3000L, persisted.getRemainingBalanceCents());
        assertEquals(GiftCardStatus.PARTIALLY_USED, persisted.getStatus());
    }

    @Test
    void redeem_amountExceedsBalance_returns400() throws Exception {
        User buyer = createActiveUser("gc-redeem-over-buyer@example.com", "Password1!");
        User redeemer = createActiveUser("gc-redeem-over-user@example.com", "Password1!");
        Company company = createCompany(buyer);
        createCard(buyer, company, "OVERAMOUNT1234AB", 1000L, GiftCardStatus.ACTIVE);

        mockMvc.perform(post("/gift-cards/redeem")
                        .header("Authorization", bearer(accessTokenFor(redeemer)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redeemBody("OVERAMOUNT1234AB", 9999L)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void redeem_voidedCard_returns410() throws Exception {
        User buyer = createActiveUser("gc-redeem-void-buyer@example.com", "Password1!");
        User redeemer = createActiveUser("gc-redeem-void-user@example.com", "Password1!");
        Company company = createCompany(buyer);
        createCard(buyer, company, "VOIDCARD12345678", 5000L, GiftCardStatus.VOID);

        mockMvc.perform(post("/gift-cards/redeem")
                        .header("Authorization", bearer(accessTokenFor(redeemer)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redeemBody("VOIDCARD12345678", 1000L)))
                .andExpect(status().isGone());
    }

    @Test
    void redeem_alreadyRedeemedCard_returns409() throws Exception {
        User buyer = createActiveUser("gc-redeem-done-buyer@example.com", "Password1!");
        User redeemer = createActiveUser("gc-redeem-done-user@example.com", "Password1!");
        Company company = createCompany(buyer);
        createCard(buyer, company, "REDEEMEDCARD1234", 5000L, GiftCardStatus.REDEEMED);

        mockMvc.perform(post("/gift-cards/redeem")
                        .header("Authorization", bearer(accessTokenFor(redeemer)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redeemBody("REDEEMEDCARD1234", 1000L)))
                .andExpect(status().isConflict());
    }

    @Test
    void redeem_unknownCode_returns404() throws Exception {
        User redeemer = createActiveUser("gc-redeem-404@example.com", "Password1!");

        mockMvc.perform(post("/gift-cards/redeem")
                        .header("Authorization", bearer(accessTokenFor(redeemer)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redeemBody("DOESNOTEXIST1234", 1000L)))
                .andExpect(status().isNotFound());
    }

    @Test
    void redeem_unauthenticated_returns401() throws Exception {
        // Provide valid body so @NotBlank/@Min validation does not fire before auth check
        mockMvc.perform(post("/gift-cards/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(redeemBody("SOMEVALIDCODE123", 1000L)))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /gift-cards/{code}/balance (no auth) ─────────────────────────────

    @Test
    void getBalance_activeCard_returns200() throws Exception {
        User buyer = createActiveUser("gc-bal-buyer@example.com", "Password1!");
        Company company = createCompany(buyer);
        createCard(buyer, company, "BALANCECHECK1234", 3000L, GiftCardStatus.ACTIVE);

        mockMvc.perform(get("/gift-cards/BALANCECHECK1234/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("BALANCECHECK1234"))
                .andExpect(jsonPath("$.data.remainingBalanceCents").value(3000))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void getBalance_partiallyUsedCard_returns200WithCorrectBalance() throws Exception {
        User buyer = createActiveUser("gc-bal-partial@example.com", "Password1!");
        Company company = createCompany(buyer);
        GiftCard card = createCard(buyer, company, "PARTIALBALANCE12", 5000L, GiftCardStatus.ACTIVE);
        card.setRemainingBalanceCents(2500L);
        card.setStatus(GiftCardStatus.PARTIALLY_USED);
        giftCardRepository.save(card);

        mockMvc.perform(get("/gift-cards/PARTIALBALANCE12/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.remainingBalanceCents").value(2500))
                .andExpect(jsonPath("$.data.status").value("PARTIALLY_USED"));
    }

    @Test
    void getBalance_unknownCode_returns404() throws Exception {
        mockMvc.perform(get("/gift-cards/UNKNOWNCODE12345/balance"))
                .andExpect(status().isNotFound());
    }

    // ── GET /gift-cards (listPurchased) ───────────────────────────────────────

    @Test
    void listPurchased_empty_returns200() throws Exception {
        User user = createActiveUser("gc-list-empty@example.com", "Password1!");

        mockMvc.perform(get("/gift-cards")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content").isEmpty())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void listPurchased_withCards_returnsPage() throws Exception {
        User buyer = createActiveUser("gc-list-buyer@example.com", "Password1!");
        Company company = createCompany(buyer);
        createCard(buyer, company, "LISTCARD1ABCDEFG", 1000L, GiftCardStatus.ACTIVE);
        createCard(buyer, company, "LISTCARD2ABCDEFG", 2000L, GiftCardStatus.REDEEMED);

        mockMvc.perform(get("/gift-cards")
                        .header("Authorization", bearer(accessTokenFor(buyer))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void listPurchased_onlyReturnsOwnCards() throws Exception {
        User buyerA = createActiveUser("gc-list-a@example.com", "Password1!");
        User buyerB = createActiveUser("gc-list-b@example.com", "Password1!");
        Company company = createCompany(buyerA);
        createCard(buyerA, company, "USERA1CARD123456", 1000L, GiftCardStatus.ACTIVE);
        createCard(buyerB, company, "USERB1CARD123456", 2000L, GiftCardStatus.ACTIVE);

        mockMvc.perform(get("/gift-cards")
                        .header("Authorization", bearer(accessTokenFor(buyerA))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].code").value("USERA1CARD123456"));

        mockMvc.perform(get("/gift-cards")
                        .header("Authorization", bearer(accessTokenFor(buyerB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].code").value("USERB1CARD123456"));
    }

    @Test
    void listPurchased_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/gift-cards"))
                .andExpect(status().isUnauthorized());
    }

    // ── DELETE /gift-cards/admin/{id}/void ────────────────────────────────────

    @Test
    void voidCard_returns204() throws Exception {
        User admin = createAdminUser("gc-void-admin@example.com");
        User buyer = createActiveUser("gc-void-buyer@example.com", "Password1!");
        Company company = createCompany(buyer);
        GiftCard card = createCard(buyer, company, "VOIDME1234567890", 5000L, GiftCardStatus.ACTIVE);

        mockMvc.perform(delete("/gift-cards/admin/" + card.getId() + "/void")
                        .header("Authorization", bearer(accessTokenFor(admin))))
                .andExpect(status().isNoContent());

        // The VOID status must be committed to the database.
        assertEquals(GiftCardStatus.VOID,
                giftCardRepository.findById(card.getId()).orElseThrow().getStatus());

        // Verify balance endpoint shows VOID status
        mockMvc.perform(get("/gift-cards/VOIDME1234567890/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("VOID"));
    }

    @Test
    void voidCard_alreadyVoided_returns400() throws Exception {
        User admin = createAdminUser("gc-void-twice-admin@example.com");
        User buyer = createActiveUser("gc-void-twice-buyer@example.com", "Password1!");
        Company company = createCompany(buyer);
        GiftCard card = createCard(buyer, company, "ALREADYVOID12345", 5000L, GiftCardStatus.VOID);

        mockMvc.perform(delete("/gift-cards/admin/" + card.getId() + "/void")
                        .header("Authorization", bearer(accessTokenFor(admin))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void voidCard_unknownCard_returns404() throws Exception {
        User admin = createAdminUser("gc-void-404-admin@example.com");

        mockMvc.perform(delete("/gift-cards/admin/" + UUID.randomUUID() + "/void")
                        .header("Authorization", bearer(accessTokenFor(admin))))
                .andExpect(status().isNotFound());
    }

    @Test
    void voidCard_nonAdmin_returns403() throws Exception {
        User user = createActiveUser("gc-void-nonadmin@example.com", "Password1!");
        User buyer = createActiveUser("gc-void-buyer2@example.com", "Password1!");
        Company company = createCompany(buyer);
        GiftCard card = createCard(buyer, company, "FORBIDDENCARD123", 5000L, GiftCardStatus.ACTIVE);

        mockMvc.perform(delete("/gift-cards/admin/" + card.getId() + "/void")
                        .header("Authorization", bearer(accessTokenFor(user))))
                .andExpect(status().isForbidden());
    }

    @Test
    void voidCard_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/gift-cards/admin/" + UUID.randomUUID() + "/void"))
                .andExpect(status().isUnauthorized());
    }
}
