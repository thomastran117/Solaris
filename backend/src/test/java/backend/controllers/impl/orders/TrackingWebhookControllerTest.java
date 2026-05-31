package backend.controllers.impl.orders;

import backend.configurations.application.GlobalExceptionHandler;
import backend.services.intf.orders.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TrackingWebhookControllerTest {

    private static final String WEBHOOK_SECRET = "test-webhook-secret";

    private OrderService orderService;
    private MockMvc mockMvc;
    private MockMvc mockMvcWithSecret;

    @BeforeEach
    void setUp() {
        orderService = mock(OrderService.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new TrackingWebhookController(orderService, mapper, ""))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvcWithSecret = MockMvcBuilders
                .standaloneSetup(new TrackingWebhookController(orderService, mapper, WEBHOOK_SECRET))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void aftershipWebhook_autoMarksDelivered_whenTagIsDelivered() throws Exception {
        String body = """
                {"msg":{"tracking":{"tracking_number":"TRACK-123","tag":"Delivered"}}}
                """;

        mockMvc.perform(post("/webhooks/aftership")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk());

        verify(orderService).autoMarkDeliveredByTracking("TRACK-123");
    }

    @Test
    void aftershipWebhook_doesNothing_whenTagIsInTransit() throws Exception {
        String body = """
                {"msg":{"tracking":{"tracking_number":"TRACK-123","tag":"InTransit"}}}
                """;

        mockMvc.perform(post("/webhooks/aftership")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk());

        verify(orderService, never()).autoMarkDeliveredByTracking(any());
    }

    @Test
    void aftershipWebhook_returns400_whenHmacSignatureIsInvalid() throws Exception {
        String body = """
                {"msg":{"tracking":{"tracking_number":"TRACK-123","tag":"Delivered"}}}
                """;

        mockMvcWithSecret.perform(post("/webhooks/aftership")
                        .contentType("application/json")
                        .header("aftership-hmac-sha256", "invalidsignature")
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(orderService, never()).autoMarkDeliveredByTracking(any());
    }

    @Test
    void aftershipWebhook_returns200_whenTrackingNumberNotFound() throws Exception {
        String body = """
                {"msg":{"tracking":{"tracking_number":"MISSING-TRACK","tag":"Delivered"}}}
                """;
        doThrow(new RuntimeException("Order not found")).when(orderService)
                .autoMarkDeliveredByTracking("MISSING-TRACK");

        mockMvc.perform(post("/webhooks/aftership")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    void aftershipWebhook_returns200_withValidHmacSignature() throws Exception {
        byte[] bodyBytes = "{\"msg\":{\"tracking\":{\"tracking_number\":\"TRACK-456\",\"tag\":\"Delivered\"}}}"
                .getBytes(StandardCharsets.UTF_8);
        String validSig = computeHmac(bodyBytes, WEBHOOK_SECRET);

        mockMvcWithSecret.perform(post("/webhooks/aftership")
                        .contentType("application/json")
                        .header("aftership-hmac-sha256", validSig)
                        .content(bodyBytes))
                .andExpect(status().isOk());

        verify(orderService).autoMarkDeliveredByTracking("TRACK-456");
    }

    private static String computeHmac(byte[] body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body));
    }
}
