package backend.controllers.impl.giftcards;

import backend.configurations.application.GlobalExceptionHandler;
import backend.dtos.responses.giftcard.GiftCardBalanceResponse;
import backend.dtos.responses.giftcard.GiftCardResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.GoneException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.enums.GiftCardStatus;
import backend.services.intf.giftcards.GiftCardService;
import backend.testutil.TestIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GiftCardControllerTest {

    private GiftCardService giftCardService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    private static final UUID USER_ID  = TestIds.uuid(1);
    private static final UUID CARD_ID  = TestIds.uuid(2);

    @BeforeEach
    void setUp() {
        giftCardService = mock(GiftCardService.class);
        GiftCardController controller = new GiftCardController(giftCardService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new NoOpValidator())
                .build();

        authenticateAs(USER_ID);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ─── POST /gift-cards/redeem ──────────────────────────────────────────────

    @Test
    void redeem_returns200WithGiftCardResponse() throws Exception {
        when(giftCardService.redeemCode(eq("ABCD1234EFGH5678"), eq(USER_ID), eq(5000L)))
                .thenReturn(makeCardResponse(CARD_ID, GiftCardStatus.REDEEMED, 0L));

        mockMvc.perform(post("/gift-cards/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("code", "ABCD1234EFGH5678", "amountCents", 5000))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CARD_ID.toString()))
                .andExpect(jsonPath("$.status").value("REDEEMED"));
    }

    @Test
    void redeem_codeNotFound_returns404() throws Exception {
        when(giftCardService.redeemCode(any(), any(), anyLong()))
                .thenThrow(new ResourceNotFoundException("Gift card not found"));

        mockMvc.perform(post("/gift-cards/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("code", "NOTEXIST12345678", "amountCents", 1000))))
                .andExpect(status().isNotFound());
    }

    @Test
    void redeem_alreadyRedeemed_returns409() throws Exception {
        when(giftCardService.redeemCode(any(), any(), anyLong()))
                .thenThrow(new ConflictException("Gift card already redeemed"));

        mockMvc.perform(post("/gift-cards/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("code", "ABCD1234EFGH5678", "amountCents", 1000))))
                .andExpect(status().isConflict());
    }

    @Test
    void redeem_voidedCard_returns410() throws Exception {
        when(giftCardService.redeemCode(any(), any(), anyLong()))
                .thenThrow(new GoneException("Gift card has been voided"));

        mockMvc.perform(post("/gift-cards/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("code", "ABCD1234EFGH5678", "amountCents", 1000))))
                .andExpect(status().isGone());
    }

    // ─── GET /gift-cards/{code}/balance ──────────────────────────────────────

    @Test
    void getBalance_returns200NoAuthRequired() throws Exception {
        SecurityContextHolder.clearContext(); // simulate unauthenticated request
        when(giftCardService.getBalance("ABCD1234EFGH5678"))
                .thenReturn(new GiftCardBalanceResponse("ABCD1234EFGH5678", 3000L, GiftCardStatus.ACTIVE));

        mockMvc.perform(get("/gift-cards/ABCD1234EFGH5678/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingBalanceCents").value(3000))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void getBalance_notFound_returns404() throws Exception {
        when(giftCardService.getBalance("NOTEXIST12345678"))
                .thenThrow(new ResourceNotFoundException("Gift card not found"));

        mockMvc.perform(get("/gift-cards/NOTEXIST12345678/balance"))
                .andExpect(status().isNotFound());
    }

    // ─── GET /gift-cards ──────────────────────────────────────────────────────

    @Test
    void listPurchased_returns200WithPage() throws Exception {
        GiftCardResponse card = makeCardResponse(CARD_ID, GiftCardStatus.ACTIVE, 5000L);
        when(giftCardService.listPurchased(eq(USER_ID), any()))
                .thenReturn(new PageImpl<>(List.of(card), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/gift-cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(CARD_ID.toString()));
    }

    // ─── DELETE /gift-cards/admin/{id}/void ───────────────────────────────────

    @Test
    void voidCard_returns204() throws Exception {
        doNothing().when(giftCardService).voidCard(CARD_ID);

        mockMvc.perform(delete("/gift-cards/admin/" + CARD_ID + "/void"))
                .andExpect(status().isNoContent());
    }

    @Test
    void voidCard_notFound_returns404() throws Exception {
        doThrow(new ResourceNotFoundException("Gift card not found"))
                .when(giftCardService).voidCard(CARD_ID);

        mockMvc.perform(delete("/gift-cards/admin/" + CARD_ID + "/void"))
                .andExpect(status().isNotFound());
    }

    @Test
    void redeem_badRequest_returns400() throws Exception {
        when(giftCardService.redeemCode(any(), any(), anyLong()))
                .thenThrow(new BadRequestException("Amount exceeds remaining balance"));

        mockMvc.perform(post("/gift-cards/redeem")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("code", "ABCD1234EFGH5678", "amountCents", 99999))))
                .andExpect(status().isBadRequest());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void authenticateAs(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private GiftCardResponse makeCardResponse(UUID cardId, GiftCardStatus status, long remaining) {
        return new GiftCardResponse(
                cardId,
                "ABCD1234EFGH5678",
                TestIds.uuid(10),
                5000L,
                remaining,
                USER_ID,
                TestIds.uuid(11),
                status,
                status == GiftCardStatus.REDEEMED ? Instant.now() : null,
                Instant.now()
        );
    }

    private static final class NoOpValidator implements Validator {
        @Override public boolean supports(Class<?> clazz) { return true; }
        @Override public void validate(Object target, Errors errors) {}
    }
}
