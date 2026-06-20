package backend.services.impl.b2b;

import backend.dtos.requests.b2b.CreateQuoteRequest;
import backend.dtos.requests.b2b.QuoteLineItemRequest;
import backend.dtos.requests.b2b.RevisedQuoteItemRequest;
import backend.dtos.requests.b2b.VendorQuoteResponseRequest;
import backend.dtos.responses.b2b.QuoteResponse;
import backend.dtos.responses.order.OrderResponse;
import backend.exceptions.http.ConflictException;
import backend.models.core.B2BAccount;
import backend.models.core.B2BQuote;
import backend.models.core.B2BQuoteItem;
import backend.models.core.Company;
import backend.models.core.Product;
import backend.models.core.User;
import backend.models.enums.PaymentTerms;
import backend.models.enums.QuoteStatus;
import backend.repositories.B2BAccountRepository;
import backend.repositories.B2BInvoiceRepository;
import backend.repositories.B2BQuoteRepository;
import backend.repositories.CompanyRepository;
import backend.repositories.ProductRepository;
import backend.repositories.ProductVariantRepository;
import backend.repositories.UserRepository;
import backend.services.intf.b2b.B2BInvoiceService;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.orders.OrderService;
import backend.services.intf.orders.QuoteOrderSpec;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class B2BQuoteServiceTest {

    @Mock B2BQuoteRepository quoteRepository;
    @Mock B2BAccountRepository accountRepository;
    @Mock B2BInvoiceRepository invoiceRepository;
    @Mock ProductRepository productRepository;
    @Mock ProductVariantRepository variantRepository;
    @Mock CompanyRepository companyRepository;
    @Mock UserRepository userRepository;
    @Mock CompanyAccessService companyAccessService;
    @Mock OrderService orderService;
    @Mock B2BInvoiceService invoiceService;
    @Mock backend.services.intf.support.EmailService emailService;

    B2BQuoteServiceImpl service;

    static final UUID COMPANY_ID = TestIds.uuid(1);
    static final UUID OWNER_ID = TestIds.uuid(2);
    static final UUID BUYER_ID = TestIds.uuid(3);
    static final UUID PRODUCT_ID = TestIds.uuid(4);
    static final UUID QUOTE_ID = TestIds.uuid(5);
    static final UUID ORDER_ID = TestIds.uuid(6);

    @BeforeEach
    void setUp() {
        service = new B2BQuoteServiceImpl(quoteRepository, accountRepository, invoiceRepository,
                productRepository, variantRepository, companyRepository, userRepository,
                companyAccessService, orderService, invoiceService, emailService, 14);
    }

    private Company mockVendor() {
        Company company = mock(Company.class);
        lenient().when(company.getId()).thenReturn(COMPANY_ID);
        lenient().when(company.getName()).thenReturn("Acme Vendor");
        User owner = mock(User.class);
        lenient().when(owner.getEmail()).thenReturn("owner@vendor.com");
        lenient().when(owner.getFirstName()).thenReturn("Olivia");
        lenient().when(company.getOwner()).thenReturn(owner);
        return company;
    }

    private Product mockProduct(String price) {
        Product product = mock(Product.class);
        Company company = mock(Company.class);
        lenient().when(company.getId()).thenReturn(COMPANY_ID);
        lenient().when(product.getId()).thenReturn(PRODUCT_ID);
        lenient().when(product.getName()).thenReturn("Widget");
        lenient().when(product.getCompany()).thenReturn(company);
        lenient().when(product.getPrice()).thenReturn(new BigDecimal(price));
        return product;
    }

    private B2BQuote quote(QuoteStatus status, PaymentTerms terms, Instant expiresAt, long unitCents, int qty) {
        B2BQuote q = new B2BQuote();
        q.setVendorCompanyId(COMPANY_ID);
        q.setBuyerUserId(BUYER_ID);
        q.setStatus(status);
        q.setPaymentTerms(terms);
        q.setExpiresAt(expiresAt);
        B2BQuoteItem item = new B2BQuoteItem();
        item.setProductId(PRODUCT_ID);
        item.setProductName("Widget");
        item.setQuantity(qty);
        item.setUnitPriceCents(unitCents);
        item.recomputeTotal();
        q.getItems().add(item);
        return q;
    }

    // ─── requestQuote ────────────────────────────────────────────────────────────

    @Test
    void shouldSnapshotPriceAndAutoCreateAccountOnRequest() {
        Company vendor = mockVendor();
        Product product = mockProduct("50.00");
        when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(vendor));
        when(accountRepository.findByUserId(BUYER_ID)).thenReturn(Optional.empty());
        when(accountRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(quoteRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CreateQuoteRequest req = new CreateQuoteRequest("Buyer Co", "TAX1", "1 St", "Need bulk",
                PaymentTerms.NET_30, List.of(new QuoteLineItemRequest(PRODUCT_ID, null, 4)));

        QuoteResponse resp = service.requestQuote(BUYER_ID, COMPANY_ID, req);

        assertThat(resp.status()).isEqualTo(QuoteStatus.PENDING_VENDOR);
        assertThat(resp.items()).hasSize(1);
        assertThat(resp.items().get(0).unitPriceCents()).isEqualTo(5000L);
        assertThat(resp.totalCents()).isEqualTo(20000L);
        verify(accountRepository).save(any());
        verify(emailService).sendQuoteReceivedEmail(eq("owner@vendor.com"), any(), eq("Buyer Co"), any(), eq(20000L));
    }

    // ─── vendorRespondToQuote ──────────────────────────────────────────────────────

    @Test
    void shouldApproveQuoteMovingToPendingBuyer() {
        Company vendor = mockVendor();
        when(companyAccessService.require(eq(COMPANY_ID), eq(OWNER_ID), any())).thenReturn(vendor);
        when(quoteRepository.findByIdAndVendorCompanyId(QUOTE_ID, COMPANY_ID))
                .thenReturn(Optional.of(quote(QuoteStatus.PENDING_VENDOR, PaymentTerms.IMMEDIATE, null, 5000, 2)));
        when(quoteRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userRepository.findById(BUYER_ID)).thenReturn(Optional.of(mock(User.class)));

        VendorQuoteResponseRequest req = new VendorQuoteResponseRequest(
                VendorQuoteResponseRequest.VendorQuoteAction.APPROVE, "Looks good", null, null);

        QuoteResponse resp = service.vendorRespondToQuote(COMPANY_ID, QUOTE_ID, OWNER_ID, req);

        assertThat(resp.status()).isEqualTo(QuoteStatus.PENDING_BUYER);
        verify(emailService).sendQuoteRespondedEmail(any(), any(), eq("Acme Vendor"), any(), eq("APPROVED"));
    }

    @Test
    void shouldReplaceLinesOnCounterOffer() {
        Company vendor = mockVendor();
        Product product = mockProduct("50.00");
        when(companyAccessService.require(eq(COMPANY_ID), eq(OWNER_ID), any())).thenReturn(vendor);
        when(quoteRepository.findByIdAndVendorCompanyId(QUOTE_ID, COMPANY_ID))
                .thenReturn(Optional.of(quote(QuoteStatus.PENDING_VENDOR, PaymentTerms.IMMEDIATE, null, 5000, 2)));
        when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(quoteRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userRepository.findById(BUYER_ID)).thenReturn(Optional.of(mock(User.class)));

        VendorQuoteResponseRequest req = new VendorQuoteResponseRequest(
                VendorQuoteResponseRequest.VendorQuoteAction.COUNTER, "Best I can do", PaymentTerms.NET_60,
                List.of(new RevisedQuoteItemRequest(PRODUCT_ID, null, 2, 4200)));

        QuoteResponse resp = service.vendorRespondToQuote(COMPANY_ID, QUOTE_ID, OWNER_ID, req);

        assertThat(resp.status()).isEqualTo(QuoteStatus.PENDING_BUYER);
        assertThat(resp.paymentTerms()).isEqualTo(PaymentTerms.NET_60);
        assertThat(resp.items().get(0).unitPriceCents()).isEqualTo(4200L);
        assertThat(resp.totalCents()).isEqualTo(8400L);
        verify(emailService).sendQuoteRespondedEmail(any(), any(), any(), any(), eq("COUNTERED"));
    }

    @Test
    void shouldRejectVendorResponseWhenNotPendingVendor() {
        Company vendor = mockVendor();
        when(companyAccessService.require(eq(COMPANY_ID), eq(OWNER_ID), any())).thenReturn(vendor);
        when(quoteRepository.findByIdAndVendorCompanyId(QUOTE_ID, COMPANY_ID))
                .thenReturn(Optional.of(quote(QuoteStatus.PENDING_BUYER, PaymentTerms.IMMEDIATE, null, 5000, 1)));

        VendorQuoteResponseRequest req = new VendorQuoteResponseRequest(
                VendorQuoteResponseRequest.VendorQuoteAction.APPROVE, null, null, null);

        assertThatThrownBy(() -> service.vendorRespondToQuote(COMPANY_ID, QUOTE_ID, OWNER_ID, req))
                .isInstanceOf(ConflictException.class);
    }

    // ─── buyerAcceptQuote ──────────────────────────────────────────────────────────

    @Test
    void shouldReturnConflictWhenAcceptingExpiredQuote() {
        when(quoteRepository.findByIdAndBuyerUserId(QUOTE_ID, BUYER_ID))
                .thenReturn(Optional.of(quote(QuoteStatus.PENDING_BUYER, PaymentTerms.IMMEDIATE,
                        Instant.now().minus(1, ChronoUnit.HOURS), 5000, 1)));

        assertThatThrownBy(() -> service.buyerAcceptQuote(QUOTE_ID, BUYER_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Conflict");
        verifyNoInteractions(orderService);
    }

    @Test
    void shouldRejectAcceptWhenNotPendingBuyer() {
        when(quoteRepository.findByIdAndBuyerUserId(QUOTE_ID, BUYER_ID))
                .thenReturn(Optional.of(quote(QuoteStatus.PENDING_VENDOR, PaymentTerms.IMMEDIATE, null, 5000, 1)));

        assertThatThrownBy(() -> service.buyerAcceptQuote(QUOTE_ID, BUYER_ID))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(orderService);
    }

    @Test
    void shouldCreateImmediateOrderAndMarkConverted() {
        when(quoteRepository.findByIdAndBuyerUserId(QUOTE_ID, BUYER_ID))
                .thenReturn(Optional.of(quote(QuoteStatus.PENDING_BUYER, PaymentTerms.IMMEDIATE,
                        Instant.now().plus(1, ChronoUnit.DAYS), 5000, 2)));
        OrderResponse order = mock(OrderResponse.class);
        when(order.getId()).thenReturn(ORDER_ID);
        ArgumentCaptor<QuoteOrderSpec> specCaptor = ArgumentCaptor.forClass(QuoteOrderSpec.class);
        when(orderService.createOrderFromQuote(specCaptor.capture())).thenReturn(order);
        when(quoteRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        OrderResponse result = service.buyerAcceptQuote(QUOTE_ID, BUYER_ID);

        assertThat(result).isSameAs(order);
        assertThat(specCaptor.getValue().immediate()).isTrue();
        assertThat(specCaptor.getValue().lines()).hasSize(1);
        verify(invoiceService, never()).issueInvoice(any(), any());
        ArgumentCaptor<B2BQuote> quoteCaptor = ArgumentCaptor.forClass(B2BQuote.class);
        verify(quoteRepository).save(quoteCaptor.capture());
        assertThat(quoteCaptor.getValue().getStatus()).isEqualTo(QuoteStatus.CONVERTED);
        assertThat(quoteCaptor.getValue().getConvertedOrderId()).isEqualTo(ORDER_ID);
    }

    @Test
    void shouldIssueInvoiceForApprovedNetTermsAcceptance() {
        when(quoteRepository.findByIdAndBuyerUserId(QUOTE_ID, BUYER_ID))
                .thenReturn(Optional.of(quote(QuoteStatus.PENDING_BUYER, PaymentTerms.NET_30,
                        Instant.now().plus(1, ChronoUnit.DAYS), 5000, 2)));
        B2BAccount account = new B2BAccount();
        account.setNetTermsApproved(true);
        account.setNetTermsLimitCents(100000);
        when(accountRepository.findByUserId(BUYER_ID)).thenReturn(Optional.of(account));
        when(invoiceRepository.sumOutstandingByBuyer(BUYER_ID)).thenReturn(0L);
        OrderResponse order = mock(OrderResponse.class);
        when(order.getId()).thenReturn(ORDER_ID);
        ArgumentCaptor<QuoteOrderSpec> specCaptor = ArgumentCaptor.forClass(QuoteOrderSpec.class);
        when(orderService.createOrderFromQuote(specCaptor.capture())).thenReturn(order);
        when(quoteRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.buyerAcceptQuote(QUOTE_ID, BUYER_ID);

        assertThat(specCaptor.getValue().immediate()).isFalse();
        verify(invoiceService).issueInvoice(ORDER_ID, QUOTE_ID);
    }

    @Test
    void shouldRejectNetTermsAcceptanceOverCreditLimit() {
        when(quoteRepository.findByIdAndBuyerUserId(QUOTE_ID, BUYER_ID))
                .thenReturn(Optional.of(quote(QuoteStatus.PENDING_BUYER, PaymentTerms.NET_30,
                        Instant.now().plus(1, ChronoUnit.DAYS), 5000, 3))); // 15000 total
        B2BAccount account = new B2BAccount();
        account.setNetTermsApproved(true);
        account.setNetTermsLimitCents(20000);
        when(accountRepository.findByUserId(BUYER_ID)).thenReturn(Optional.of(account));
        when(invoiceRepository.sumOutstandingByBuyer(BUYER_ID)).thenReturn(8000L); // 8000 + 15000 > 20000

        assertThatThrownBy(() -> service.buyerAcceptQuote(QUOTE_ID, BUYER_ID))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(orderService);
    }

    @Test
    void shouldRejectNetTermsAcceptanceWhenNotApproved() {
        when(quoteRepository.findByIdAndBuyerUserId(QUOTE_ID, BUYER_ID))
                .thenReturn(Optional.of(quote(QuoteStatus.PENDING_BUYER, PaymentTerms.NET_30,
                        Instant.now().plus(1, ChronoUnit.DAYS), 5000, 1)));
        B2BAccount account = new B2BAccount();
        account.setNetTermsApproved(false);
        when(accountRepository.findByUserId(BUYER_ID)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.buyerAcceptQuote(QUOTE_ID, BUYER_ID))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(orderService);
    }

    // ─── buyerRejectQuote ──────────────────────────────────────────────────────────

    @Test
    void shouldRejectPendingBuyerQuote() {
        B2BQuote q = quote(QuoteStatus.PENDING_BUYER, PaymentTerms.IMMEDIATE, null, 5000, 1);
        when(quoteRepository.findByIdAndBuyerUserId(QUOTE_ID, BUYER_ID)).thenReturn(Optional.of(q));
        when(quoteRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.buyerRejectQuote(QUOTE_ID, BUYER_ID);

        assertThat(q.getStatus()).isEqualTo(QuoteStatus.REJECTED);
    }

    // ─── expireStaleQuotes ─────────────────────────────────────────────────────────

    @Test
    void shouldExpireOnlyStalePendingBuyerQuotes() {
        B2BQuote q1 = quote(QuoteStatus.PENDING_BUYER, PaymentTerms.IMMEDIATE, Instant.now().minus(1, ChronoUnit.DAYS), 5000, 1);
        B2BQuote q2 = quote(QuoteStatus.PENDING_BUYER, PaymentTerms.IMMEDIATE, Instant.now().minus(2, ChronoUnit.DAYS), 5000, 1);
        when(quoteRepository.findByStatusAndExpiresAtBefore(eq(QuoteStatus.PENDING_BUYER), any()))
                .thenReturn(List.of(q1, q2));
        when(quoteRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.expireStaleQuotes();

        assertThat(q1.getStatus()).isEqualTo(QuoteStatus.EXPIRED);
        assertThat(q2.getStatus()).isEqualTo(QuoteStatus.EXPIRED);
        verify(quoteRepository, times(2)).save(any());
    }
}
