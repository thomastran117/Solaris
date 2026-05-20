package backend.services.impl.payments;

import backend.configurations.environment.EnvironmentSetting;
import backend.exceptions.http.BadRequestException;
import backend.services.intf.payments.PaymentService;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Refund;
import com.stripe.model.RefundCollection;
import com.stripe.model.Transfer;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.support.RetryTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StripePaymentServiceImplTest {

    private EnvironmentSetting environmentSetting;
    private RetryTemplate retryTemplate;
    private StripePaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        environmentSetting = new EnvironmentSetting();
        environmentSetting.getStripe().setSecretKey("sk_test_123");
        environmentSetting.getStripe().setWebhookSecret("whsec_123");
        retryTemplate = mock(RetryTemplate.class);
        doAnswer(inv -> {
            RetryCallback<?, ?> callback = inv.getArgument(0);
            return callback.doWithRetry(null);
        }).when(retryTemplate).execute(any());
        service = new StripePaymentServiceImpl(environmentSetting, retryTemplate);
    }

    @AfterEach
    void tearDown() {
        Stripe.apiKey = null;
    }

    @Test
    void init_setsStripeApiKey() {
        service.init();

        assertEquals("sk_test_123", Stripe.apiKey);
    }

    @Test
    void constructWebhookEvent_extractsRefundMetadata() throws Exception {
        Event event = mock(Event.class);
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        Charge charge = mock(Charge.class);
        RefundCollection refundCollection = mock(RefundCollection.class);
        Refund refund = mock(Refund.class);

        when(event.getId()).thenReturn("evt_1");
        when(event.getType()).thenReturn("charge.refunded");
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(deserializer.getObject()).thenReturn(Optional.of(charge));
        when(charge.getId()).thenReturn("ch_1");
        when(charge.getRefunds()).thenReturn(refundCollection);
        when(refundCollection.getData()).thenReturn(List.of(refund));
        when(refund.getId()).thenReturn("re_1");
        when(refund.getStatus()).thenReturn("succeeded");
        when(refund.getAmount()).thenReturn(900L);

        try (MockedStatic<Webhook> webhook = org.mockito.Mockito.mockStatic(Webhook.class)) {
            webhook.when(() -> Webhook.constructEvent("{}", "sig", "whsec_123")).thenReturn(event);

            PaymentService.WebhookEvent result = service.constructWebhookEvent("{}", "sig");

            assertEquals("evt_1", result.eventId());
            assertEquals("charge.refunded", result.type());
            assertEquals("ch_1", result.objectId());
            assertEquals("charge", result.objectType());
            assertEquals("re_1", result.metadata().get("refundId"));
            assertEquals("900", result.metadata().get("refundAmountCents"));
        }
    }

    @Test
    void constructWebhookEvent_invalidSignatureThrowsBadRequest() {
        try (MockedStatic<Webhook> webhook = org.mockito.Mockito.mockStatic(Webhook.class)) {
            webhook.when(() -> Webhook.constructEvent("{}", "sig", "whsec_123"))
                    .thenThrow(mock(SignatureVerificationException.class));

            assertThrows(BadRequestException.class, () -> service.constructWebhookEvent("{}", "sig"));
        }
    }

    @Test
    void createTransfer_mapsStripeTransferResult() throws Exception {
        Transfer transfer = mock(Transfer.class);
        when(transfer.getId()).thenReturn("tr_123");
        when(transfer.getAmount()).thenReturn(1500L);
        when(transfer.getCurrency()).thenReturn("usd");

        try (MockedStatic<Transfer> transferStatic = org.mockito.Mockito.mockStatic(Transfer.class)) {
            transferStatic.when(() -> Transfer.create(org.mockito.ArgumentMatchers.<com.stripe.param.TransferCreateParams>any()))
                    .thenReturn(transfer);

            PaymentService.TransferResult result = service.createTransfer(
                    "acct_123", 1500L, "USD", "group_1", java.util.Map.of("vendorId", "1"));

            assertEquals("tr_123", result.transferId());
            assertEquals(1500L, result.amountCents());
            assertEquals("usd", result.currency());
            assertEquals("pending", result.status());
        }
    }
}
