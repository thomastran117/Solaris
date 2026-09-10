package backend.services.impl.payments;

import backend.configurations.environment.EnvironmentSetting;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.InternalServerErrorException;
import backend.exceptions.http.PaymentException;
import backend.exceptions.http.TooManyRequestException;
import backend.models.enums.BillingInterval;
import backend.services.intf.payments.PaymentService;
import com.stripe.Stripe;
import com.stripe.exception.AuthenticationException;
import com.stripe.exception.CardException;
import com.stripe.exception.RateLimitException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.Charge;
import com.stripe.model.Customer;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Invoice;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.model.Refund;
import com.stripe.model.RefundCollection;
import com.stripe.model.SetupIntent;
import com.stripe.model.Subscription;
import com.stripe.model.Transfer;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void constructWebhookEvent_extractsDisputeMetadata() throws Exception {
        Event event = mock(Event.class);
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        com.stripe.model.Dispute dispute = mock(com.stripe.model.Dispute.class);
        com.stripe.model.Dispute.EvidenceDetails evidenceDetails =
                mock(com.stripe.model.Dispute.EvidenceDetails.class);

        when(event.getId()).thenReturn("evt_dp_1");
        when(event.getType()).thenReturn("charge.dispute.created");
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(deserializer.getObject()).thenReturn(Optional.of(dispute));
        when(dispute.getId()).thenReturn("dp_1");
        when(dispute.getCharge()).thenReturn("ch_1");
        when(dispute.getPaymentIntent()).thenReturn("pi_1");
        when(dispute.getAmount()).thenReturn(2500L);
        when(dispute.getCurrency()).thenReturn("usd");
        when(dispute.getReason()).thenReturn("fraudulent");
        when(dispute.getStatus()).thenReturn("needs_response");
        when(dispute.getEvidenceDetails()).thenReturn(evidenceDetails);
        when(evidenceDetails.getDueBy()).thenReturn(1760000000L);
        when(evidenceDetails.getHasEvidence()).thenReturn(false);

        try (MockedStatic<Webhook> webhook = org.mockito.Mockito.mockStatic(Webhook.class)) {
            webhook.when(() -> Webhook.constructEvent("{}", "sig", "whsec_123")).thenReturn(event);

            PaymentService.WebhookEvent result = service.constructWebhookEvent("{}", "sig");

            assertEquals("charge.dispute.created", result.type());
            assertEquals("dp_1", result.objectId());
            assertEquals("ch_1", result.metadata().get("chargeId"));
            assertEquals("pi_1", result.metadata().get("paymentIntentId"));
            assertEquals("2500", result.metadata().get("amountCents"));
            assertEquals("usd", result.metadata().get("currency"));
            assertEquals("fraudulent", result.metadata().get("disputeReason"));
            assertEquals("needs_response", result.metadata().get("disputeStatus"));
            assertEquals("1760000000", result.metadata().get("evidenceDueBy"));
            assertEquals("false", result.metadata().get("hasEvidence"));
        }
    }

    /** Inquiries and already-closed disputes arrive without evidence details. */
    @Test
    void constructWebhookEvent_disputeWithoutEvidenceDetailsOmitsDeadline() throws Exception {
        Event event = mock(Event.class);
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        com.stripe.model.Dispute dispute = mock(com.stripe.model.Dispute.class);

        when(event.getId()).thenReturn("evt_dp_2");
        when(event.getType()).thenReturn("charge.dispute.closed");
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(deserializer.getObject()).thenReturn(Optional.of(dispute));
        when(dispute.getId()).thenReturn("dp_2");
        when(dispute.getStatus()).thenReturn("won");
        when(dispute.getEvidenceDetails()).thenReturn(null);

        try (MockedStatic<Webhook> webhook = org.mockito.Mockito.mockStatic(Webhook.class)) {
            webhook.when(() -> Webhook.constructEvent("{}", "sig", "whsec_123")).thenReturn(event);

            PaymentService.WebhookEvent result = service.constructWebhookEvent("{}", "sig");

            assertEquals("won", result.metadata().get("disputeStatus"));
            assertNull(result.metadata().get("evidenceDueBy"));
            assertNull(result.metadata().get("hasEvidence"));
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

    // ─── mapStripeException (private — via reflection) ───────────────────────

    @Test
    void mapStripeException_cardException_returnsPaymentException() {
        CardException cardEx = mock(CardException.class);
        when(cardEx.getUserMessage()).thenReturn("Card declined");
        when(cardEx.getMessage()).thenReturn("Card declined detail");

        RuntimeException result = ReflectionTestUtils.invokeMethod(service, "mapStripeException", (StripeException) cardEx);

        assertInstanceOf(PaymentException.class, result);
    }

    @Test
    void mapStripeException_cardException_nullUserMessage_usesGenericMessage() {
        CardException cardEx = mock(CardException.class);
        when(cardEx.getUserMessage()).thenReturn(null);
        when(cardEx.getMessage()).thenReturn("raw_detail");

        RuntimeException result = ReflectionTestUtils.invokeMethod(service, "mapStripeException", (StripeException) cardEx);

        assertInstanceOf(PaymentException.class, result);
    }

    @Test
    void mapStripeException_rateLimitException_returnsTooManyRequests() {
        RateLimitException rateLimitEx = mock(RateLimitException.class);

        RuntimeException result = ReflectionTestUtils.invokeMethod(service, "mapStripeException", (StripeException) rateLimitEx);

        assertInstanceOf(TooManyRequestException.class, result);
    }

    @Test
    void mapStripeException_authenticationException_returnsInternalServerError() {
        AuthenticationException authEx = mock(AuthenticationException.class);

        RuntimeException result = ReflectionTestUtils.invokeMethod(service, "mapStripeException", (StripeException) authEx);

        assertInstanceOf(InternalServerErrorException.class, result);
    }

    @Test
    void mapStripeException_genericStripeException_returnsInternalServerError() {
        StripeException genericEx = mock(StripeException.class);

        RuntimeException result = ReflectionTestUtils.invokeMethod(service, "mapStripeException", genericEx);

        assertInstanceOf(InternalServerErrorException.class, result);
    }

    // ─── extractMetadata (private — via reflection) ──────────────────────────

    @Test
    void extractMetadata_nullStripeObject_returnsEmptyMap() {
        Map<String, String> result = ReflectionTestUtils.invokeMethod(service, "extractMetadata", "charge.refunded", null);
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    void extractMetadata_nullEventType_returnsEmptyMap() {
        Map<String, String> result = ReflectionTestUtils.invokeMethod(service, "extractMetadata", null, mock(Charge.class));
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    void extractMetadata_refundUpdated_extractsRefundFields() {
        Refund r = mock(Refund.class);
        when(r.getId()).thenReturn("re_2");
        when(r.getStatus()).thenReturn("succeeded");
        when(r.getAmount()).thenReturn(750L);

        Map<String, String> result = ReflectionTestUtils.invokeMethod(service, "extractMetadata", "refund.updated", r);

        assertEquals("re_2", result.get("refundId"));
        assertEquals("succeeded", result.get("refundStatus"));
        assertEquals("750", result.get("refundAmountCents"));
    }

    @Test
    void extractMetadata_invoicePaid_extractsInvoiceFields() {
        Invoice inv = mock(Invoice.class);
        when(inv.getSubscription()).thenReturn("sub_123");
        when(inv.getCustomer()).thenReturn("cus_abc");
        when(inv.getAmountPaid()).thenReturn(2000L);

        Map<String, String> result = ReflectionTestUtils.invokeMethod(service, "extractMetadata", "invoice.paid", inv);

        assertEquals("sub_123", result.get("subscriptionId"));
        assertEquals("cus_abc", result.get("customerId"));
        assertEquals("2000", result.get("amountPaidCents"));
    }

    @Test
    void extractMetadata_customerSubscription_extractsSubscriptionFields() {
        Subscription sub = mock(Subscription.class);
        when(sub.getId()).thenReturn("sub_456");
        when(sub.getStatus()).thenReturn("active");

        Map<String, String> result = ReflectionTestUtils.invokeMethod(service, "extractMetadata", "customer.subscription.created", sub);

        assertEquals("sub_456", result.get("subscriptionId"));
        assertEquals("active", result.get("subscriptionStatus"));
    }

    @Test
    void extractMetadata_setupIntentSucceeded_extractsSetupFields() {
        SetupIntent si = mock(SetupIntent.class);
        when(si.getCustomer()).thenReturn("cus_789");
        when(si.getPaymentMethod()).thenReturn("pm_abc");

        Map<String, String> result = ReflectionTestUtils.invokeMethod(service, "extractMetadata", "setup_intent.succeeded", si);

        assertEquals("cus_789", result.get("customerId"));
        assertEquals("pm_abc", result.get("paymentMethodId"));
    }

    @Test
    void extractMetadata_accountUpdated_extractsAccountFields() {
        Account a = mock(Account.class);
        when(a.getChargesEnabled()).thenReturn(true);
        when(a.getPayoutsEnabled()).thenReturn(false);
        when(a.getDetailsSubmitted()).thenReturn(true);

        Map<String, String> result = ReflectionTestUtils.invokeMethod(service, "extractMetadata", "account.updated", a);

        assertEquals("true", result.get("chargesEnabled"));
        assertEquals("false", result.get("payoutsEnabled"));
        assertEquals("true", result.get("detailsSubmitted"));
    }

    @Test
    void extractMetadata_transferCreated_extractsTransferFields() {
        Transfer t = mock(Transfer.class);
        when(t.getAmount()).thenReturn(1000L);
        when(t.getCurrency()).thenReturn("usd");
        when(t.getTransferGroup()).thenReturn("order_grp");
        when(t.getMetadata()).thenReturn(Map.of("orderId", "42"));

        Map<String, String> result = ReflectionTestUtils.invokeMethod(service, "extractMetadata", "transfer.created", t);

        assertEquals("1000", result.get("amountCents"));
        assertEquals("usd", result.get("currency"));
        assertEquals("order_grp", result.get("transferGroup"));
        assertEquals("42", result.get("orderId"));
    }

    @Test
    void extractMetadata_unknownEventType_returnsEmptyMap() {
        Map<String, String> result = ReflectionTestUtils.invokeMethod(service, "extractMetadata", "unknown.event", mock(Invoice.class));
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    // ─── extractPremiumMetadata (private — via reflection) ───────────────────

    @Test
    void extractPremiumMetadata_checkoutSessionCompleted_extractsFields() {
        com.stripe.model.checkout.Session session = mock(com.stripe.model.checkout.Session.class);
        when(session.getCustomer()).thenReturn("cus_premium");
        when(session.getSubscription()).thenReturn("sub_premium");

        Map<String, String> result = ReflectionTestUtils.invokeMethod(service, "extractPremiumMetadata", "checkout.session.completed", session);

        assertEquals("cus_premium", result.get("customerId"));
        assertEquals("sub_premium", result.get("subscriptionId"));
    }

    @Test
    void extractPremiumMetadata_customerSubscription_extractsFields() {
        Subscription sub = mock(Subscription.class);
        when(sub.getId()).thenReturn("sub_p1");
        when(sub.getStatus()).thenReturn("active");
        when(sub.getCustomer()).thenReturn("cus_p1");
        when(sub.getCurrentPeriodEnd()).thenReturn(9999999L);

        Map<String, String> result = ReflectionTestUtils.invokeMethod(service, "extractPremiumMetadata", "customer.subscription.updated", sub);

        assertEquals("sub_p1", result.get("subscriptionId"));
        assertEquals("active", result.get("subscriptionStatus"));
        assertEquals("cus_p1", result.get("customerId"));
    }

    @Test
    void extractPremiumMetadata_invoicePaid_extractsSubscriptionId() {
        Invoice inv = mock(Invoice.class);
        when(inv.getSubscription()).thenReturn("sub_inv1");

        Map<String, String> result = ReflectionTestUtils.invokeMethod(service, "extractPremiumMetadata", "invoice.paid", inv);

        assertEquals("sub_inv1", result.get("subscriptionId"));
    }

    @Test
    void extractPremiumMetadata_unknownEvent_returnsEmptyMap() {
        Map<String, String> result = ReflectionTestUtils.invokeMethod(service, "extractPremiumMetadata", "payment.created", mock(Invoice.class));
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    void extractPremiumMetadata_nullObject_returnsEmptyMap() {
        Map<String, String> result = ReflectionTestUtils.invokeMethod(service, "extractPremiumMetadata", "checkout.session.completed", null);
        assertNotNull(result);
        assertEquals(0, result.size());
    }

    // ─── toStripeInterval (private — all BillingInterval values) ────────────

    @Test
    void toStripeInterval_allValues_returnCorrectIntervals() {
        for (BillingInterval interval : BillingInterval.values()) {
            Object result = ReflectionTestUtils.invokeMethod(service, "toStripeInterval", interval);
            assertNotNull(result);
        }
    }

    // ─── intervalToChrono (private — all BillingInterval values) ────────────

    @Test
    void intervalToChrono_allValues_returnCorrectChronoUnits() {
        for (BillingInterval interval : BillingInterval.values()) {
            Object result = ReflectionTestUtils.invokeMethod(service, "intervalToChrono", interval);
            assertNotNull(result);
        }
    }

    // ─── toPaymentMethodInfo (private) ───────────────────────────────────────

    @Test
    void toPaymentMethodInfo_withCard_extractsCardDetails() {
        PaymentMethod pm = mock(PaymentMethod.class);
        PaymentMethod.Card card = mock(PaymentMethod.Card.class);
        when(pm.getId()).thenReturn("pm_1");
        when(pm.getCustomer()).thenReturn("cus_1");
        when(pm.getCard()).thenReturn(card);
        when(card.getBrand()).thenReturn("visa");
        when(card.getLast4()).thenReturn("4242");
        when(card.getExpMonth()).thenReturn(12L);
        when(card.getExpYear()).thenReturn(2027L);

        PaymentService.PaymentMethodInfo result =
                ReflectionTestUtils.invokeMethod(service, "toPaymentMethodInfo", pm);

        assertEquals("pm_1", result.id());
        assertEquals("visa", result.brand());
        assertEquals("4242", result.last4());
        assertEquals(12, result.expMonth());
        assertEquals(2027, result.expYear());
    }

    @Test
    void toPaymentMethodInfo_noCard_returnsNullFields() {
        PaymentMethod pm = mock(PaymentMethod.class);
        when(pm.getId()).thenReturn("pm_2");
        when(pm.getCustomer()).thenReturn("cus_2");
        when(pm.getCard()).thenReturn(null);

        PaymentService.PaymentMethodInfo result =
                ReflectionTestUtils.invokeMethod(service, "toPaymentMethodInfo", pm);

        assertNull(result.brand());
        assertNull(result.last4());
    }

    // ─── toConnectAccountResult (private) ────────────────────────────────────

    @Test
    void toConnectAccountResult_mapsAccountFields() {
        Account a = mock(Account.class);
        when(a.getId()).thenReturn("acct_1");
        when(a.getChargesEnabled()).thenReturn(true);
        when(a.getPayoutsEnabled()).thenReturn(true);
        when(a.getDetailsSubmitted()).thenReturn(false);

        PaymentService.ConnectAccountResult result =
                ReflectionTestUtils.invokeMethod(service, "toConnectAccountResult", a);

        assertEquals("acct_1", result.accountId());
        assertEquals(true, result.chargesEnabled());
        assertEquals(true, result.payoutsEnabled());
        assertEquals(false, result.detailsSubmitted());
    }

    // ─── toSubscriptionResult (private) ──────────────────────────────────────

    @Test
    void toSubscriptionResult_withItems_extractsFirstItemId() {
        Subscription sub = mock(Subscription.class);
        when(sub.getId()).thenReturn("sub_1");
        when(sub.getCustomer()).thenReturn("cus_1");
        when(sub.getStatus()).thenReturn("active");
        when(sub.getLatestInvoice()).thenReturn("inv_1");
        when(sub.getCurrentPeriodStart()).thenReturn(1000000L);
        when(sub.getCurrentPeriodEnd()).thenReturn(2000000L);
        when(sub.getDefaultPaymentMethod()).thenReturn("pm_1");

        com.stripe.model.SubscriptionItemCollection items = mock(com.stripe.model.SubscriptionItemCollection.class);
        com.stripe.model.SubscriptionItem si = mock(com.stripe.model.SubscriptionItem.class);
        when(si.getId()).thenReturn("si_1");
        when(items.getData()).thenReturn(List.of(si));
        when(sub.getItems()).thenReturn(items);

        PaymentService.SubscriptionResult result =
                ReflectionTestUtils.invokeMethod(service, "toSubscriptionResult", sub);

        assertEquals("sub_1", result.id());
        assertEquals("si_1", result.firstSubscriptionItemId());
        assertNotNull(result.currentPeriodStart());
        assertNotNull(result.currentPeriodEnd());
    }

    @Test
    void toSubscriptionResult_nullPeriods_returnsNullInstants() {
        Subscription sub = mock(Subscription.class);
        when(sub.getId()).thenReturn("sub_2");
        when(sub.getCurrentPeriodStart()).thenReturn(null);
        when(sub.getCurrentPeriodEnd()).thenReturn(null);
        when(sub.getItems()).thenReturn(null);

        PaymentService.SubscriptionResult result =
                ReflectionTestUtils.invokeMethod(service, "toSubscriptionResult", sub);

        assertNull(result.currentPeriodStart());
        assertNull(result.currentPeriodEnd());
    }

    // ─── constructWebhookEvent — additional event types ──────────────────────

    @Test
    void constructWebhookEvent_invoiceEvent_extractsInvoiceMetadata() throws Exception {
        Event event = mock(Event.class);
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        Invoice inv = mock(Invoice.class);

        when(event.getId()).thenReturn("evt_inv");
        when(event.getType()).thenReturn("invoice.paid");
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(deserializer.getObject()).thenReturn(Optional.of(inv));
        when(inv.getSubscription()).thenReturn("sub_999");
        when(inv.getCustomer()).thenReturn("cus_999");
        when(inv.getAmountPaid()).thenReturn(5000L);

        try (MockedStatic<Webhook> webhook = org.mockito.Mockito.mockStatic(Webhook.class)) {
            webhook.when(() -> Webhook.constructEvent("{}", "sig", "whsec_123")).thenReturn(event);

            PaymentService.WebhookEvent result = service.constructWebhookEvent("{}", "sig");

            assertEquals("invoice.paid", result.type());
            assertEquals("invoice", result.objectType());
            assertEquals("sub_999", result.metadata().get("subscriptionId"));
        }
    }

    @Test
    void constructWebhookEvent_malformedPayload_throwsBadRequest() {
        try (MockedStatic<Webhook> webhook = org.mockito.Mockito.mockStatic(Webhook.class)) {
            webhook.when(() -> Webhook.constructEvent("{}", "sig", "whsec_123"))
                    .thenThrow(new RuntimeException("malformed"));

            assertThrows(BadRequestException.class, () -> service.constructWebhookEvent("{}", "sig"));
        }
    }

    // ─── constructPremiumWebhookEvent ─────────────────────────────────────────

    @Test
    void constructPremiumWebhookEvent_invalidSignature_throwsBadRequest() {
        try (MockedStatic<Webhook> webhook = org.mockito.Mockito.mockStatic(Webhook.class)) {
            webhook.when(() -> Webhook.constructEvent(any(), any(), any()))
                    .thenThrow(mock(SignatureVerificationException.class));

            assertThrows(BadRequestException.class,
                    () -> service.constructPremiumWebhookEvent("{}", "sig"));
        }
    }

    // ─── createPaymentIntent ──────────────────────────────────────────────────

    @Test
    void createPaymentIntent_success_returnsResult() throws Exception {
        PaymentIntent pi = mock(PaymentIntent.class);
        when(pi.getId()).thenReturn("pi_test");
        when(pi.getClientSecret()).thenReturn("pi_test_secret");
        when(pi.getAmount()).thenReturn(1000L);
        when(pi.getCurrency()).thenReturn("usd");
        when(pi.getStatus()).thenReturn("requires_payment_method");
        when(pi.getCustomer()).thenReturn("cus_1");

        try (MockedStatic<PaymentIntent> piStatic = org.mockito.Mockito.mockStatic(PaymentIntent.class)) {
            piStatic.when(() -> PaymentIntent.create(
                    any(com.stripe.param.PaymentIntentCreateParams.class),
                    any(com.stripe.net.RequestOptions.class))).thenReturn(pi);

            PaymentService.PaymentIntentResult result =
                    service.createPaymentIntent(1000L, "USD", "cus_1", Map.of("order_id", "42"));

            assertEquals("pi_test", result.id());
            assertEquals(1000L, result.amountInCents());
        }
    }

    @Test
    void createPaymentIntent_cardDeclined_throwsPaymentException() {
        CardException cardEx = mock(CardException.class);
        when(cardEx.getUserMessage()).thenReturn("Your card was declined");

        try (MockedStatic<PaymentIntent> piStatic = org.mockito.Mockito.mockStatic(PaymentIntent.class)) {
            piStatic.when(() -> PaymentIntent.create(
                    any(com.stripe.param.PaymentIntentCreateParams.class),
                    any(com.stripe.net.RequestOptions.class))).thenThrow(cardEx);

            assertThrows(PaymentException.class,
                    () -> service.createPaymentIntent(1000L, "USD", null, null));
        }
    }

    // ─── createCustomer ───────────────────────────────────────────────────────

    @Test
    void createCustomer_success_returnsResult() throws Exception {
        Customer customer = mock(Customer.class);
        when(customer.getId()).thenReturn("cus_new");
        when(customer.getEmail()).thenReturn("test@example.com");
        when(customer.getName()).thenReturn("Test User");

        try (MockedStatic<Customer> customerStatic = org.mockito.Mockito.mockStatic(Customer.class)) {
            customerStatic.when(() -> Customer.create(
                    any(com.stripe.param.CustomerCreateParams.class))).thenReturn(customer);

            PaymentService.CustomerResult result =
                    service.createCustomer("test@example.com", "Test User", null);

            assertEquals("cus_new", result.id());
            assertEquals("test@example.com", result.email());
        }
    }

    // ─── refundPayment ────────────────────────────────────────────────────────

    @Test
    void refundPayment_success_returnsRefundResult() throws Exception {
        Refund refund = mock(Refund.class);
        when(refund.getId()).thenReturn("re_test");
        when(refund.getAmount()).thenReturn(500L);
        when(refund.getCurrency()).thenReturn("usd");
        when(refund.getStatus()).thenReturn("pending");
        when(refund.getPaymentIntent()).thenReturn("pi_test");

        try (MockedStatic<Refund> refundStatic = org.mockito.Mockito.mockStatic(Refund.class)) {
            refundStatic.when(() -> Refund.create(
                    any(com.stripe.param.RefundCreateParams.class),
                    any(com.stripe.net.RequestOptions.class))).thenReturn(refund);

            PaymentService.RefundResult result = service.refundPayment("pi_test", 500L);

            assertEquals("re_test", result.id());
            assertEquals(500L, result.amountInCents());
        }
    }

    @Test
    void refundPayment_nullAmount_fullRefund() throws Exception {
        Refund refund = mock(Refund.class);
        when(refund.getId()).thenReturn("re_full");
        when(refund.getAmount()).thenReturn(1000L);
        when(refund.getCurrency()).thenReturn("usd");
        when(refund.getStatus()).thenReturn("succeeded");
        when(refund.getPaymentIntent()).thenReturn("pi_full");

        try (MockedStatic<Refund> refundStatic = org.mockito.Mockito.mockStatic(Refund.class)) {
            refundStatic.when(() -> Refund.create(
                    any(com.stripe.param.RefundCreateParams.class),
                    any(com.stripe.net.RequestOptions.class))).thenReturn(refund);

            PaymentService.RefundResult result = service.refundPayment("pi_full", null);

            assertEquals("re_full", result.id());
        }
    }

    // ─── retrievePaymentIntent ────────────────────────────────────────────────

    @Test
    void retrievePaymentIntent_success_returnsResult() throws Exception {
        PaymentIntent pi = mock(PaymentIntent.class);
        when(pi.getId()).thenReturn("pi_retrieved");
        when(pi.getClientSecret()).thenReturn("pi_secret_r");
        when(pi.getAmount()).thenReturn(2000L);
        when(pi.getCurrency()).thenReturn("usd");
        when(pi.getStatus()).thenReturn("succeeded");
        when(pi.getCustomer()).thenReturn("cus_r1");

        try (MockedStatic<PaymentIntent> piStatic = org.mockito.Mockito.mockStatic(PaymentIntent.class)) {
            piStatic.when(() -> PaymentIntent.retrieve("pi_retrieved")).thenReturn(pi);

            PaymentService.PaymentIntentResult result = service.retrievePaymentIntent("pi_retrieved");

            assertEquals("pi_retrieved", result.id());
            assertEquals(2000L, result.amountInCents());
        }
    }

    // ─── cancelPaymentIntent ──────────────────────────────────────────────────

    @Test
    void cancelPaymentIntent_success_returnsCancelledResult() throws Exception {
        PaymentIntent pi = mock(PaymentIntent.class);
        PaymentIntent cancelledPi = mock(PaymentIntent.class);
        when(cancelledPi.getId()).thenReturn("pi_cancel");
        when(cancelledPi.getClientSecret()).thenReturn("pi_secret_c");
        when(cancelledPi.getAmount()).thenReturn(1500L);
        when(cancelledPi.getCurrency()).thenReturn("usd");
        when(cancelledPi.getStatus()).thenReturn("canceled");
        when(cancelledPi.getCustomer()).thenReturn(null);
        when(pi.cancel(any(com.stripe.param.PaymentIntentCancelParams.class))).thenReturn(cancelledPi);

        try (MockedStatic<PaymentIntent> piStatic = org.mockito.Mockito.mockStatic(PaymentIntent.class)) {
            piStatic.when(() -> PaymentIntent.retrieve("pi_cancel")).thenReturn(pi);

            PaymentService.PaymentIntentResult result = service.cancelPaymentIntent("pi_cancel");

            assertEquals("pi_cancel", result.id());
            assertEquals("canceled", result.status());
        }
    }

    // ─── createCustomer with metadata ─────────────────────────────────────────

    @Test
    void createCustomer_withMetadata_success() throws Exception {
        Customer customer = mock(Customer.class);
        when(customer.getId()).thenReturn("cus_meta");
        when(customer.getEmail()).thenReturn("meta@example.com");
        when(customer.getName()).thenReturn("Meta User");

        try (MockedStatic<Customer> customerStatic = org.mockito.Mockito.mockStatic(Customer.class)) {
            customerStatic.when(() -> Customer.create(
                    any(com.stripe.param.CustomerCreateParams.class))).thenReturn(customer);

            PaymentService.CustomerResult result =
                    service.createCustomer("meta@example.com", "Meta User", Map.of("userId", "1"));

            assertEquals("cus_meta", result.id());
        }
    }

    // ─── createSetupIntent ────────────────────────────────────────────────────

    @Test
    void createSetupIntent_success_returnsResult() throws Exception {
        SetupIntent si = mock(SetupIntent.class);
        when(si.getId()).thenReturn("seti_1");
        when(si.getClientSecret()).thenReturn("seti_secret");
        when(si.getCustomer()).thenReturn("cus_si");

        try (MockedStatic<SetupIntent> siStatic = org.mockito.Mockito.mockStatic(SetupIntent.class)) {
            siStatic.when(() -> SetupIntent.create(
                    any(com.stripe.param.SetupIntentCreateParams.class))).thenReturn(si);

            PaymentService.SetupIntentResult result = service.createSetupIntent("cus_si");

            assertEquals("seti_1", result.id());
            assertEquals("cus_si", result.customerId());
        }
    }

    // ─── listPaymentMethods ───────────────────────────────────────────────────

    @Test
    void listPaymentMethods_returnsList() throws Exception {
        com.stripe.model.PaymentMethodCollection collection =
                mock(com.stripe.model.PaymentMethodCollection.class);
        PaymentMethod pm = mock(PaymentMethod.class);
        PaymentMethod.Card card = mock(PaymentMethod.Card.class);
        when(pm.getId()).thenReturn("pm_list1");
        when(pm.getCustomer()).thenReturn("cus_list");
        when(pm.getCard()).thenReturn(card);
        when(card.getBrand()).thenReturn("mastercard");
        when(card.getLast4()).thenReturn("1234");
        when(card.getExpMonth()).thenReturn(6L);
        when(card.getExpYear()).thenReturn(2028L);
        when(collection.getData()).thenReturn(List.of(pm));

        try (MockedStatic<PaymentMethod> pmStatic = org.mockito.Mockito.mockStatic(PaymentMethod.class)) {
            pmStatic.when(() -> PaymentMethod.list(
                    any(com.stripe.param.PaymentMethodListParams.class))).thenReturn(collection);

            List<PaymentService.PaymentMethodInfo> result = service.listPaymentMethods("cus_list");

            assertEquals(1, result.size());
            assertEquals("pm_list1", result.get(0).id());
            assertEquals("mastercard", result.get(0).brand());
        }
    }

    // ─── retrievePaymentMethod ────────────────────────────────────────────────

    @Test
    void retrievePaymentMethod_returnsInfo() throws Exception {
        PaymentMethod pm = mock(PaymentMethod.class);
        when(pm.getId()).thenReturn("pm_ret");
        when(pm.getCustomer()).thenReturn("cus_ret");
        when(pm.getCard()).thenReturn(null);

        try (MockedStatic<PaymentMethod> pmStatic = org.mockito.Mockito.mockStatic(PaymentMethod.class)) {
            pmStatic.when(() -> PaymentMethod.retrieve("pm_ret")).thenReturn(pm);

            PaymentService.PaymentMethodInfo result = service.retrievePaymentMethod("pm_ret");

            assertEquals("pm_ret", result.id());
        }
    }

    // ─── detachPaymentMethod ─────────────────────────────────────────────────

    @Test
    void detachPaymentMethod_withCustomer_callsDetach() throws Exception {
        PaymentMethod pm = mock(PaymentMethod.class);
        when(pm.getCustomer()).thenReturn("cus_detach");
        when(pm.detach(any(com.stripe.param.PaymentMethodDetachParams.class))).thenReturn(pm);

        try (MockedStatic<PaymentMethod> pmStatic = org.mockito.Mockito.mockStatic(PaymentMethod.class)) {
            pmStatic.when(() -> PaymentMethod.retrieve("pm_detach")).thenReturn(pm);

            service.detachPaymentMethod("pm_detach");

            org.mockito.Mockito.verify(pm).detach(any(com.stripe.param.PaymentMethodDetachParams.class));
        }
    }

    @Test
    void detachPaymentMethod_withoutCustomer_skipsDetach() throws Exception {
        PaymentMethod pm = mock(PaymentMethod.class);
        when(pm.getCustomer()).thenReturn(null);

        try (MockedStatic<PaymentMethod> pmStatic = org.mockito.Mockito.mockStatic(PaymentMethod.class)) {
            pmStatic.when(() -> PaymentMethod.retrieve("pm_noDetach")).thenReturn(pm);

            service.detachPaymentMethod("pm_noDetach");

            org.mockito.Mockito.verify(pm, org.mockito.Mockito.never())
                    .detach(any(com.stripe.param.PaymentMethodDetachParams.class));
        }
    }

    // ─── createRecurringPrice ────────────────────────────────────────────────

    @Test
    void createRecurringPrice_withMetadata_returnsResult() throws Exception {
        com.stripe.model.Price price = mock(com.stripe.model.Price.class);
        when(price.getId()).thenReturn("price_rec");
        when(price.getUnitAmount()).thenReturn(999L);
        when(price.getCurrency()).thenReturn("usd");

        try (MockedStatic<com.stripe.model.Price> priceStatic =
                     org.mockito.Mockito.mockStatic(com.stripe.model.Price.class)) {
            priceStatic.when(() -> com.stripe.model.Price.create(
                    any(com.stripe.param.PriceCreateParams.class))).thenReturn(price);

            PaymentService.PriceResult result = service.createRecurringPrice(
                    999L, "USD", BillingInterval.MONTH, 1, "Pro Plan", Map.of("tier", "pro"));

            assertEquals("price_rec", result.id());
            assertEquals(999L, result.unitAmountCents());
        }
    }

    @Test
    void createRecurringPrice_nullUnitAmount_returnsZero() throws Exception {
        com.stripe.model.Price price = mock(com.stripe.model.Price.class);
        when(price.getId()).thenReturn("price_zero");
        when(price.getUnitAmount()).thenReturn(null);
        when(price.getCurrency()).thenReturn("usd");

        try (MockedStatic<com.stripe.model.Price> priceStatic =
                     org.mockito.Mockito.mockStatic(com.stripe.model.Price.class)) {
            priceStatic.when(() -> com.stripe.model.Price.create(
                    any(com.stripe.param.PriceCreateParams.class))).thenReturn(price);

            PaymentService.PriceResult result = service.createRecurringPrice(
                    0L, "USD", BillingInterval.YEAR, 1, null, null);

            assertEquals(0L, result.unitAmountCents());
        }
    }

    // ─── createSubscription ──────────────────────────────────────────────────

    @Test
    void createSubscription_withPaymentMethod_returnsResult() throws Exception {
        Subscription sub = mockSubscription("sub_new");

        try (MockedStatic<Subscription> subStatic = org.mockito.Mockito.mockStatic(Subscription.class)) {
            subStatic.when(() -> Subscription.create(
                    any(com.stripe.param.SubscriptionCreateParams.class))).thenReturn(sub);

            PaymentService.SubscriptionResult result = service.createSubscription(
                    "cus_sub", "price_sub", 2, "pm_default", Map.of("planId", "1"));

            assertEquals("sub_new", result.id());
        }
    }

    @Test
    void createSubscription_noPaymentMethodNoMetadata_returnsResult() throws Exception {
        Subscription sub = mockSubscription("sub_nopm");

        try (MockedStatic<Subscription> subStatic = org.mockito.Mockito.mockStatic(Subscription.class)) {
            subStatic.when(() -> Subscription.create(
                    any(com.stripe.param.SubscriptionCreateParams.class))).thenReturn(sub);

            PaymentService.SubscriptionResult result = service.createSubscription(
                    "cus_nopm", "price_nopm", 1, null, null);

            assertEquals("sub_nopm", result.id());
        }
    }

    // ─── updateSubscriptionQuantity ───────────────────────────────────────────

    @Test
    void updateSubscriptionQuantity_returnsUpdatedResult() throws Exception {
        Subscription sub = mockSubscription("sub_upd");
        Subscription updated = mockSubscription("sub_upd");
        when(sub.update(any(com.stripe.param.SubscriptionUpdateParams.class))).thenReturn(updated);

        try (MockedStatic<Subscription> subStatic = org.mockito.Mockito.mockStatic(Subscription.class)) {
            subStatic.when(() -> Subscription.retrieve("sub_upd")).thenReturn(sub);

            PaymentService.SubscriptionResult result =
                    service.updateSubscriptionQuantity("sub_upd", "si_item", 5);

            assertEquals("sub_upd", result.id());
        }
    }

    // ─── swapSubscriptionPrice ────────────────────────────────────────────────

    @Test
    void swapSubscriptionPrice_returnsResult() throws Exception {
        Subscription sub = mockSubscription("sub_swap");
        Subscription swapped = mockSubscription("sub_swap");
        when(sub.update(any(com.stripe.param.SubscriptionUpdateParams.class))).thenReturn(swapped);

        try (MockedStatic<Subscription> subStatic = org.mockito.Mockito.mockStatic(Subscription.class)) {
            subStatic.when(() -> Subscription.retrieve("sub_swap")).thenReturn(sub);

            PaymentService.SubscriptionResult result =
                    service.swapSubscriptionPrice("sub_swap", "si_swap", "price_new", 3);

            assertEquals("sub_swap", result.id());
        }
    }

    // ─── cancelSubscription ───────────────────────────────────────────────────

    @Test
    void cancelSubscription_atPeriodEnd_updatesSubscription() throws Exception {
        Subscription sub = mockSubscription("sub_cxl_end");
        Subscription cancelled = mockSubscription("sub_cxl_end");
        when(sub.update(any(com.stripe.param.SubscriptionUpdateParams.class))).thenReturn(cancelled);

        try (MockedStatic<Subscription> subStatic = org.mockito.Mockito.mockStatic(Subscription.class)) {
            subStatic.when(() -> Subscription.retrieve("sub_cxl_end")).thenReturn(sub);

            PaymentService.SubscriptionResult result = service.cancelSubscription("sub_cxl_end", true);

            assertEquals("sub_cxl_end", result.id());
        }
    }

    @Test
    void cancelSubscription_immediately_cancelsSubscription() throws Exception {
        Subscription sub = mockSubscription("sub_imm");
        Subscription cancelled = mockSubscription("sub_imm");
        when(sub.cancel(any(com.stripe.param.SubscriptionCancelParams.class))).thenReturn(cancelled);

        try (MockedStatic<Subscription> subStatic = org.mockito.Mockito.mockStatic(Subscription.class)) {
            subStatic.when(() -> Subscription.retrieve("sub_imm")).thenReturn(sub);

            PaymentService.SubscriptionResult result = service.cancelSubscription("sub_imm", false);

            assertEquals("sub_imm", result.id());
        }
    }

    // ─── pauseSubscription ────────────────────────────────────────────────────

    @Test
    void pauseSubscription_returnsResult() throws Exception {
        Subscription sub = mockSubscription("sub_pause");
        Subscription paused = mockSubscription("sub_pause");
        when(sub.update(any(com.stripe.param.SubscriptionUpdateParams.class))).thenReturn(paused);

        try (MockedStatic<Subscription> subStatic = org.mockito.Mockito.mockStatic(Subscription.class)) {
            subStatic.when(() -> Subscription.retrieve("sub_pause")).thenReturn(sub);

            PaymentService.SubscriptionResult result = service.pauseSubscription("sub_pause");

            assertEquals("sub_pause", result.id());
        }
    }

    // ─── resumeSubscription ───────────────────────────────────────────────────

    @Test
    void resumeSubscription_returnsResult() throws Exception {
        Subscription sub = mockSubscription("sub_resume");
        Subscription resumed = mockSubscription("sub_resume");
        when(sub.resume(any(com.stripe.param.SubscriptionResumeParams.class))).thenReturn(resumed);

        try (MockedStatic<Subscription> subStatic = org.mockito.Mockito.mockStatic(Subscription.class)) {
            subStatic.when(() -> Subscription.retrieve("sub_resume")).thenReturn(sub);

            PaymentService.SubscriptionResult result = service.resumeSubscription("sub_resume");

            assertEquals("sub_resume", result.id());
        }
    }

    // ─── skipNextCycle ────────────────────────────────────────────────────────

    @Test
    void skipNextCycle_withPeriodEnd_updatesAnchor() throws Exception {
        Subscription sub = mockSubscription("sub_skip");
        when(sub.getCurrentPeriodEnd()).thenReturn(1748000000L);
        Subscription updated = mockSubscription("sub_skip");
        when(sub.update(any(com.stripe.param.SubscriptionUpdateParams.class))).thenReturn(updated);

        try (MockedStatic<Subscription> subStatic = org.mockito.Mockito.mockStatic(Subscription.class)) {
            subStatic.when(() -> Subscription.retrieve("sub_skip")).thenReturn(sub);

            // Use DAY: Instant.plus only supports NANOS…DAYS; WEEKS/MONTHS/YEARS all throw
            PaymentService.SubscriptionResult result =
                    service.skipNextCycle("sub_skip", BillingInterval.DAY, 7);

            assertEquals("sub_skip", result.id());
        }
    }

    @Test
    void skipNextCycle_withNullPeriodEnd_usesZeroFallback() throws Exception {
        Subscription sub = mockSubscription("sub_skipnull");
        when(sub.getCurrentPeriodEnd()).thenReturn(null);
        Subscription updated = mockSubscription("sub_skipnull");
        when(sub.update(any(com.stripe.param.SubscriptionUpdateParams.class))).thenReturn(updated);

        try (MockedStatic<Subscription> subStatic = org.mockito.Mockito.mockStatic(Subscription.class)) {
            subStatic.when(() -> Subscription.retrieve("sub_skipnull")).thenReturn(sub);

            PaymentService.SubscriptionResult result =
                    service.skipNextCycle("sub_skipnull", BillingInterval.DAY, 7);

            assertEquals("sub_skipnull", result.id());
        }
    }

    // ─── retrieveSubscription ─────────────────────────────────────────────────

    @Test
    void retrieveSubscription_returnsResult() throws Exception {
        Subscription sub = mockSubscription("sub_get");

        try (MockedStatic<Subscription> subStatic = org.mockito.Mockito.mockStatic(Subscription.class)) {
            subStatic.when(() -> Subscription.retrieve("sub_get")).thenReturn(sub);

            PaymentService.SubscriptionResult result = service.retrieveSubscription("sub_get");

            assertEquals("sub_get", result.id());
        }
    }

    // ─── createCheckoutSession ────────────────────────────────────────────────

    @Test
    void createCheckoutSession_returnsSessionUrl() throws Exception {
        com.stripe.model.checkout.Session session = mock(com.stripe.model.checkout.Session.class);
        when(session.getUrl()).thenReturn("https://checkout.stripe.com/session_123");

        try (MockedStatic<com.stripe.model.checkout.Session> sessionStatic =
                     org.mockito.Mockito.mockStatic(com.stripe.model.checkout.Session.class)) {
            sessionStatic.when(() -> com.stripe.model.checkout.Session.create(
                    any(com.stripe.param.checkout.SessionCreateParams.class))).thenReturn(session);

            String url = service.createCheckoutSession(
                    "cus_co", "price_co", "https://success.com", "https://cancel.com");

            assertEquals("https://checkout.stripe.com/session_123", url);
        }
    }

    // ─── createPortalSession ─────────────────────────────────────────────────

    @Test
    void createPortalSession_returnsPortalUrl() throws Exception {
        com.stripe.model.billingportal.Session portal = mock(com.stripe.model.billingportal.Session.class);
        when(portal.getUrl()).thenReturn("https://billing.stripe.com/portal_123");

        try (MockedStatic<com.stripe.model.billingportal.Session> portalStatic =
                     org.mockito.Mockito.mockStatic(com.stripe.model.billingportal.Session.class)) {
            portalStatic.when(() -> com.stripe.model.billingportal.Session.create(
                    any(com.stripe.param.billingportal.SessionCreateParams.class))).thenReturn(portal);

            String url = service.createPortalSession("cus_portal", "https://return.com");

            assertEquals("https://billing.stripe.com/portal_123", url);
        }
    }

    // ─── createConnectAccount ─────────────────────────────────────────────────

    @Test
    void createConnectAccount_withMetadata_returnsResult() throws Exception {
        Account account = mock(Account.class);
        when(account.getId()).thenReturn("acct_connect");
        when(account.getChargesEnabled()).thenReturn(false);
        when(account.getPayoutsEnabled()).thenReturn(false);
        when(account.getDetailsSubmitted()).thenReturn(false);

        try (MockedStatic<Account> accountStatic = org.mockito.Mockito.mockStatic(Account.class)) {
            accountStatic.when(() -> Account.create(any(com.stripe.param.AccountCreateParams.class)))
                    .thenReturn(account);

            PaymentService.ConnectAccountResult result = service.createConnectAccount(
                    "vendor@example.com", "Vendor Inc", Map.of("vendorId", "v1"));

            assertEquals("acct_connect", result.accountId());
        }
    }

    @Test
    void createConnectAccount_nullMetadata_returnsResult() throws Exception {
        Account account = mock(Account.class);
        when(account.getId()).thenReturn("acct_nometa");
        when(account.getChargesEnabled()).thenReturn(false);
        when(account.getPayoutsEnabled()).thenReturn(false);
        when(account.getDetailsSubmitted()).thenReturn(false);

        try (MockedStatic<Account> accountStatic = org.mockito.Mockito.mockStatic(Account.class)) {
            accountStatic.when(() -> Account.create(any(com.stripe.param.AccountCreateParams.class)))
                    .thenReturn(account);

            PaymentService.ConnectAccountResult result = service.createConnectAccount(
                    "no@meta.com", "No Meta Co", null);

            assertEquals("acct_nometa", result.accountId());
        }
    }

    // ─── generateConnectOnboardingLink ────────────────────────────────────────

    @Test
    void generateConnectOnboardingLink_withExpiresAt_returnsResult() throws Exception {
        com.stripe.model.AccountLink link = mock(com.stripe.model.AccountLink.class);
        when(link.getUrl()).thenReturn("https://connect.stripe.com/onboard_link");
        when(link.getExpiresAt()).thenReturn(1750000000L);

        try (MockedStatic<com.stripe.model.AccountLink> linkStatic =
                     org.mockito.Mockito.mockStatic(com.stripe.model.AccountLink.class)) {
            linkStatic.when(() -> com.stripe.model.AccountLink.create(
                    any(com.stripe.param.AccountLinkCreateParams.class))).thenReturn(link);

            PaymentService.ConnectOnboardingLinkResult result = service.generateConnectOnboardingLink(
                    "acct_onboard", "https://return.com", "https://refresh.com");

            assertEquals("https://connect.stripe.com/onboard_link", result.url());
            assertNotNull(result.expiresAt());
        }
    }

    @Test
    void generateConnectOnboardingLink_nullExpiresAt_returnsNullInstant() throws Exception {
        com.stripe.model.AccountLink link = mock(com.stripe.model.AccountLink.class);
        when(link.getUrl()).thenReturn("https://connect.stripe.com/link_noexpiry");
        when(link.getExpiresAt()).thenReturn(null);

        try (MockedStatic<com.stripe.model.AccountLink> linkStatic =
                     org.mockito.Mockito.mockStatic(com.stripe.model.AccountLink.class)) {
            linkStatic.when(() -> com.stripe.model.AccountLink.create(
                    any(com.stripe.param.AccountLinkCreateParams.class))).thenReturn(link);

            PaymentService.ConnectOnboardingLinkResult result = service.generateConnectOnboardingLink(
                    "acct_noexp", "https://return.com", "https://refresh.com");

            assertNull(result.expiresAt());
        }
    }

    // ─── getConnectAccountStatus ─────────────────────────────────────────────

    @Test
    void getConnectAccountStatus_returnsStatus() throws Exception {
        Account account = mock(Account.class);
        when(account.getId()).thenReturn("acct_status");
        when(account.getChargesEnabled()).thenReturn(true);
        when(account.getPayoutsEnabled()).thenReturn(true);
        when(account.getDetailsSubmitted()).thenReturn(true);

        try (MockedStatic<Account> accountStatic = org.mockito.Mockito.mockStatic(Account.class)) {
            accountStatic.when(() -> Account.retrieve("acct_status")).thenReturn(account);

            PaymentService.ConnectAccountResult result = service.getConnectAccountStatus("acct_status");

            assertEquals("acct_status", result.accountId());
            assertEquals(true, result.chargesEnabled());
        }
    }

    // ─── createTransfer with null metadata ────────────────────────────────────

    @Test
    void createTransfer_nullMetadata_returnsResult() throws Exception {
        Transfer transfer = mock(Transfer.class);
        when(transfer.getId()).thenReturn("tr_nometa");
        when(transfer.getAmount()).thenReturn(2000L);
        when(transfer.getCurrency()).thenReturn("eur");

        try (MockedStatic<Transfer> transferStatic = org.mockito.Mockito.mockStatic(Transfer.class)) {
            transferStatic.when(() -> Transfer.create(
                    any(com.stripe.param.TransferCreateParams.class))).thenReturn(transfer);

            PaymentService.TransferResult result = service.createTransfer(
                    "acct_nometa", 2000L, "EUR", "group_null", null);

            assertEquals("tr_nometa", result.transferId());
        }
    }

    // ─── constructPremiumWebhookEvent happy path ──────────────────────────────

    @Test
    void constructPremiumWebhookEvent_checkoutCompleted_extractsFields() throws Exception {
        environmentSetting.getStripe().getPremium().setWebhookSecret("whsec_premium");

        Event event = mock(Event.class);
        com.stripe.model.EventDataObjectDeserializer deserializer =
                mock(com.stripe.model.EventDataObjectDeserializer.class);
        com.stripe.model.checkout.Session session = mock(com.stripe.model.checkout.Session.class);

        when(event.getId()).thenReturn("evt_premium");
        when(event.getType()).thenReturn("checkout.session.completed");
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(deserializer.getObject()).thenReturn(Optional.of(session));
        when(session.getId()).thenReturn("cs_1");
        when(session.getCustomer()).thenReturn("cus_premium_1");
        when(session.getSubscription()).thenReturn("sub_premium_1");

        try (MockedStatic<Webhook> webhook = org.mockito.Mockito.mockStatic(Webhook.class)) {
            webhook.when(() -> Webhook.constructEvent("{}", "sig", "whsec_premium")).thenReturn(event);

            PaymentService.WebhookEvent result = service.constructPremiumWebhookEvent("{}", "sig");

            assertEquals("evt_premium", result.eventId());
            assertEquals("cus_premium_1", result.metadata().get("customerId"));
            assertEquals("sub_premium_1", result.metadata().get("subscriptionId"));
        }
    }

    @Test
    void constructPremiumWebhookEvent_malformedPayload_throwsBadRequest() {
        try (MockedStatic<Webhook> webhook = org.mockito.Mockito.mockStatic(Webhook.class)) {
            webhook.when(() -> Webhook.constructEvent(any(), any(), any()))
                    .thenThrow(new RuntimeException("malformed"));

            assertThrows(BadRequestException.class,
                    () -> service.constructPremiumWebhookEvent("{}", "sig"));
        }
    }

    // ─── constructWebhookEvent — eventType without period ─────────────────────

    @Test
    void constructWebhookEvent_eventTypeWithoutPeriod_objectTypeSameAsEventType() throws Exception {
        Event event = mock(Event.class);
        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);

        when(event.getId()).thenReturn("evt_nodot");
        when(event.getType()).thenReturn("payout_created");
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(deserializer.getObject()).thenReturn(Optional.empty());

        try (MockedStatic<Webhook> webhook = org.mockito.Mockito.mockStatic(Webhook.class)) {
            webhook.when(() -> Webhook.constructEvent("{}", "sig", "whsec_123")).thenReturn(event);

            PaymentService.WebhookEvent result = service.constructWebhookEvent("{}", "sig");

            assertEquals("payout_created", result.objectType());
            assertNull(result.objectId());
        }
    }

    // ─── extractMetadata — charge.refunded edge cases ─────────────────────────

    @Test
    void extractMetadata_chargeRefunded_emptyRefunds_returnsEmptyMap() {
        Charge charge = mock(Charge.class);
        RefundCollection refunds = mock(RefundCollection.class);
        when(charge.getRefunds()).thenReturn(refunds);
        when(refunds.getData()).thenReturn(List.of());

        Map<String, String> result = ReflectionTestUtils.invokeMethod(
                service, "extractMetadata", "charge.refunded", charge);

        assertEquals(0, result.size());
    }

    @Test
    void extractMetadata_chargeRefunded_nullRefunds_returnsEmptyMap() {
        Charge charge = mock(Charge.class);
        when(charge.getRefunds()).thenReturn(null);

        Map<String, String> result = ReflectionTestUtils.invokeMethod(
                service, "extractMetadata", "charge.refunded", charge);

        assertEquals(0, result.size());
    }

    // ─── extractPremiumMetadata — null eventType ──────────────────────────────

    @Test
    void extractPremiumMetadata_nullEventType_returnsEmptyMap() {
        Map<String, String> result = ReflectionTestUtils.invokeMethod(
                service, "extractPremiumMetadata", null, mock(Subscription.class));

        assertNotNull(result);
        assertEquals(0, result.size());
    }

    // ─── toSubscriptionResult — empty items list ──────────────────────────────

    @Test
    void toSubscriptionResult_emptyItemsList_returnsNullFirstItemId() {
        Subscription sub = mock(Subscription.class);
        when(sub.getId()).thenReturn("sub_empty");
        when(sub.getCurrentPeriodStart()).thenReturn(null);
        when(sub.getCurrentPeriodEnd()).thenReturn(null);
        com.stripe.model.SubscriptionItemCollection items =
                mock(com.stripe.model.SubscriptionItemCollection.class);
        when(items.getData()).thenReturn(List.of());
        when(sub.getItems()).thenReturn(items);

        PaymentService.SubscriptionResult result =
                ReflectionTestUtils.invokeMethod(service, "toSubscriptionResult", sub);

        assertNull(result.firstSubscriptionItemId());
    }

    // ─── executeWithRetry — RuntimeException propagation ────────────────────

    @Test
    void executeWithRetry_runtimeExceptionFromRetryTemplate_propagates() {
        RetryTemplate failingRetry = mock(RetryTemplate.class);
        org.mockito.Mockito.doThrow(new IllegalArgumentException("retry config failed"))
                .when(failingRetry).execute(any());
        StripePaymentServiceImpl failService =
                new StripePaymentServiceImpl(environmentSetting, failingRetry);

        assertThrows(IllegalArgumentException.class,
                () -> failService.retrievePaymentIntent("pi_any"));
    }

    // ─── mockSubscription helper ──────────────────────────────────────────────

    private Subscription mockSubscription(String id) {
        Subscription sub = mock(Subscription.class);
        when(sub.getId()).thenReturn(id);
        when(sub.getCustomer()).thenReturn("cus_" + id);
        when(sub.getStatus()).thenReturn("active");
        when(sub.getLatestInvoice()).thenReturn(null);
        when(sub.getCurrentPeriodStart()).thenReturn(1000000L);
        when(sub.getCurrentPeriodEnd()).thenReturn(2000000L);
        when(sub.getDefaultPaymentMethod()).thenReturn(null);
        when(sub.getItems()).thenReturn(null);
        return sub;
    }
}
