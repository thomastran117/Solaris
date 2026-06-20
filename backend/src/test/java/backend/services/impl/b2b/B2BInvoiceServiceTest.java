package backend.services.impl.b2b;

import backend.dtos.responses.b2b.B2BInvoiceResponse;
import backend.models.core.B2BInvoice;
import backend.models.core.B2BQuote;
import backend.models.core.B2BQuoteItem;
import backend.models.core.Order;
import backend.models.enums.InvoiceStatus;
import backend.models.enums.PaymentTerms;
import backend.repositories.B2BInvoiceRepository;
import backend.repositories.B2BQuoteRepository;
import backend.repositories.CompanyRepository;
import backend.repositories.OrderRepository;
import backend.repositories.UserRepository;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.support.EmailService;
import backend.testutil.TestIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class B2BInvoiceServiceTest {

    @Mock B2BInvoiceRepository invoiceRepository;
    @Mock B2BQuoteRepository quoteRepository;
    @Mock OrderRepository orderRepository;
    @Mock CompanyRepository companyRepository;
    @Mock UserRepository userRepository;
    @Mock CompanyAccessService companyAccessService;
    @Mock EmailService emailService;

    B2BInvoiceServiceImpl service;

    static final UUID COMPANY_ID = TestIds.uuid(1);
    static final UUID OWNER_ID = TestIds.uuid(2);
    static final UUID BUYER_ID = TestIds.uuid(3);
    static final UUID ORDER_ID = TestIds.uuid(4);
    static final UUID QUOTE_ID = TestIds.uuid(5);
    static final UUID INVOICE_ID = TestIds.uuid(6);

    @BeforeEach
    void setUp() {
        service = new B2BInvoiceServiceImpl(invoiceRepository, quoteRepository, orderRepository,
                companyRepository, userRepository, companyAccessService, emailService);
    }

    private B2BQuote quoteWithItem(long unitCents, int qty) {
        B2BQuote quote = new B2BQuote();
        quote.setVendorCompanyId(COMPANY_ID);
        quote.setBuyerUserId(BUYER_ID);
        quote.setPaymentTerms(PaymentTerms.NET_30);
        B2BQuoteItem item = new B2BQuoteItem();
        item.setProductId(TestIds.uuid(20));
        item.setProductName("Widget");
        item.setQuantity(qty);
        item.setUnitPriceCents(unitCents);
        item.recomputeTotal();
        quote.getItems().add(item);
        return quote;
    }

    @Test
    void shouldIssueInvoiceWithDueDateAndIssuedStatus() {
        when(invoiceRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());
        when(quoteRepository.findById(QUOTE_ID)).thenReturn(Optional.of(quoteWithItem(5000, 3)));
        Order order = new Order();
        order.setCurrency("usd");
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(invoiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        backend.models.core.User buyer = mock(backend.models.core.User.class);
        when(buyer.getEmail()).thenReturn("buyer@example.com");
        when(userRepository.findById(BUYER_ID)).thenReturn(Optional.of(buyer));

        B2BInvoiceResponse resp = service.issueInvoice(ORDER_ID, QUOTE_ID);

        assertThat(resp.status()).isEqualTo(InvoiceStatus.ISSUED);
        assertThat(resp.totalCents()).isEqualTo(15000L);
        // due ~30 days out
        long days = ChronoUnit.DAYS.between(Instant.now(), resp.dueDateAt());
        assertThat(days).isBetween(29L, 30L);
        verify(emailService).sendInvoiceIssuedEmail(any(), any(), any(), any(), eq(15000L), any(), any(), anyList());
    }

    @Test
    void shouldReturnExistingInvoiceWhenAlreadyIssuedForOrder() {
        B2BInvoice existing = new B2BInvoice();
        existing.setOrderId(ORDER_ID);
        existing.setStatus(InvoiceStatus.ISSUED);
        existing.setInvoiceNumber("INV-1");
        when(invoiceRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(existing));

        B2BInvoiceResponse resp = service.issueInvoice(ORDER_ID, QUOTE_ID);

        assertThat(resp.invoiceNumber()).isEqualTo("INV-1");
        verify(invoiceRepository, never()).save(any());
        verifyNoInteractions(emailService);
    }

    @Test
    void shouldMarkInvoicePaidAndStampOrderPaidAt() {
        B2BInvoice invoice = new B2BInvoice();
        invoice.setOrderId(ORDER_ID);
        invoice.setStatus(InvoiceStatus.ISSUED);
        when(invoiceRepository.findByIdAndVendorCompanyId(INVOICE_ID, COMPANY_ID))
                .thenReturn(Optional.of(invoice));
        Order order = new Order();
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        service.markInvoicePaid(COMPANY_ID, INVOICE_ID, OWNER_ID, "wire-123");

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoice.getPaidAt()).isNotNull();
        assertThat(invoice.getPaymentReference()).isEqualTo("wire-123");
        assertThat(order.getPaidAt()).isNotNull();
        verify(orderRepository).save(order);
    }

    @Test
    void shouldBeIdempotentWhenInvoiceAlreadyPaid() {
        B2BInvoice invoice = new B2BInvoice();
        invoice.setStatus(InvoiceStatus.PAID);
        when(invoiceRepository.findByIdAndVendorCompanyId(INVOICE_ID, COMPANY_ID))
                .thenReturn(Optional.of(invoice));

        service.markInvoicePaid(COMPANY_ID, INVOICE_ID, OWNER_ID, "ref");

        verify(invoiceRepository, never()).save(any());
        verify(orderRepository, never()).findById(any());
    }

    @Test
    void shouldFlipPastDueIssuedInvoicesToOverdue() {
        B2BInvoice a = new B2BInvoice();
        a.setStatus(InvoiceStatus.ISSUED);
        a.setDueDateAt(Instant.now().minus(3, ChronoUnit.DAYS));
        B2BInvoice b = new B2BInvoice();
        b.setStatus(InvoiceStatus.ISSUED);
        b.setDueDateAt(Instant.now().minus(1, ChronoUnit.DAYS));
        when(invoiceRepository.findByStatusAndDueDateAtBefore(eq(InvoiceStatus.ISSUED), any()))
                .thenReturn(List.of(a, b));
        when(invoiceRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        int count = service.markOverdueInvoices();

        assertThat(count).isEqualTo(2);
        assertThat(a.getStatus()).isEqualTo(InvoiceStatus.OVERDUE);
        assertThat(b.getStatus()).isEqualTo(InvoiceStatus.OVERDUE);
        verify(invoiceRepository, times(2)).save(any());
    }

    @Test
    void shouldReturnOnlyPastDueUnpaidInvoicesAsOverdue() {
        B2BInvoice overdue = new B2BInvoice();
        overdue.setStatus(InvoiceStatus.ISSUED);
        overdue.setDueDateAt(Instant.now().minus(2, ChronoUnit.DAYS));
        overdue.setInvoiceNumber("INV-OVERDUE");
        B2BInvoice future = new B2BInvoice();
        future.setStatus(InvoiceStatus.ISSUED);
        future.setDueDateAt(Instant.now().plus(5, ChronoUnit.DAYS));
        future.setInvoiceNumber("INV-FUTURE");
        when(invoiceRepository.findByVendorCompanyIdAndStatusIn(eq(COMPANY_ID), anyList()))
                .thenReturn(List.of(overdue, future));

        List<B2BInvoiceResponse> result = service.getOverdueInvoices(COMPANY_ID, OWNER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).invoiceNumber()).isEqualTo("INV-OVERDUE");
    }
}
