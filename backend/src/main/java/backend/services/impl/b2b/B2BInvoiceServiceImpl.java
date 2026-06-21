package backend.services.impl.b2b;

import backend.dtos.responses.b2b.B2BInvoiceResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.events.email.EmailEvent;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.B2BInvoice;
import backend.models.core.B2BQuote;
import backend.models.core.Company;
import backend.models.core.Order;
import backend.models.core.User;
import backend.models.enums.CompanyCapability;
import backend.models.enums.InvoiceStatus;
import backend.repositories.B2BInvoiceRepository;
import backend.repositories.B2BQuoteRepository;
import backend.repositories.CompanyRepository;
import backend.repositories.OrderRepository;
import backend.repositories.UserRepository;
import backend.services.intf.b2b.B2BInvoiceService;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.support.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class B2BInvoiceServiceImpl implements B2BInvoiceService {

    private static final Logger log = LoggerFactory.getLogger(B2BInvoiceServiceImpl.class);

    private final B2BInvoiceRepository invoiceRepository;
    private final B2BQuoteRepository quoteRepository;
    private final OrderRepository orderRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final CompanyAccessService companyAccessService;
    private final EmailService emailService;

    public B2BInvoiceServiceImpl(B2BInvoiceRepository invoiceRepository,
                                 B2BQuoteRepository quoteRepository,
                                 OrderRepository orderRepository,
                                 CompanyRepository companyRepository,
                                 UserRepository userRepository,
                                 CompanyAccessService companyAccessService,
                                 EmailService emailService) {
        this.invoiceRepository = invoiceRepository;
        this.quoteRepository = quoteRepository;
        this.orderRepository = orderRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.companyAccessService = companyAccessService;
        this.emailService = emailService;
    }

    @Override
    @Transactional
    public B2BInvoiceResponse issueInvoice(UUID orderId, UUID quoteId) {
        // Idempotent: one invoice per order.
        B2BInvoice existing = invoiceRepository.findByOrderId(orderId).orElse(null);
        if (existing != null) {
            return B2BInvoiceResponse.from(existing);
        }

        B2BQuote quote = quoteRepository.findById(quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Quote not found with id: " + quoteId));
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        long totalCents = quote.totalCents();
        Instant due = Instant.now().plus(quote.getPaymentTerms().netDays(), java.time.temporal.ChronoUnit.DAYS);

        B2BInvoice invoice = new B2BInvoice();
        invoice.setOrderId(orderId);
        invoice.setQuoteId(quoteId);
        invoice.setVendorCompanyId(quote.getVendorCompanyId());
        invoice.setBuyerUserId(quote.getBuyerUserId());
        invoice.setInvoiceNumber(generateInvoiceNumber());
        invoice.setDueDateAt(due);
        invoice.setTotalCents(totalCents);
        invoice.setStatus(InvoiceStatus.ISSUED);
        B2BInvoice saved = invoiceRepository.save(invoice);

        // Notify the buyer with a PDF invoice (best-effort, after commit).
        try {
            User buyer = userRepository.findById(quote.getBuyerUserId()).orElse(null);
            Company vendor = companyRepository.findById(quote.getVendorCompanyId()).orElse(null);
            String currency = order.getCurrency() != null ? order.getCurrency() : "usd";
            LocalDate dueDate = LocalDate.ofInstant(due, ZoneOffset.UTC);
            List<EmailEvent.InvoiceLineItem> lines = quote.getItems().stream()
                    .map(i -> new EmailEvent.InvoiceLineItem(
                            i.getProductName() != null ? i.getProductName() : "Item",
                            i.getQuantity(), i.getUnitPriceCents(), i.getTotalPriceCents()))
                    .toList();
            if (buyer != null) {
                emailService.sendInvoiceIssuedEmail(
                        buyer.getEmail(), buyer.getFirstName(),
                        vendor != null ? vendor.getName() : "ShopWave Vendor",
                        saved.getInvoiceNumber(), totalCents, dueDate, currency, lines);
            }
        } catch (Exception e) {
            log.warn("[B2B] Failed to queue invoice email for order {}: {}", orderId, e.getMessage());
        }

        return B2BInvoiceResponse.from(saved);
    }

    @Override
    @Transactional
    public void markInvoicePaid(UUID companyId, UUID invoiceId, UUID ownerId, String paymentReference) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_QUOTES);

        B2BInvoice invoice = invoiceRepository.findByIdAndVendorCompanyId(invoiceId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found with id: " + invoiceId));

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            return; // idempotent
        }
        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(Instant.now());
        invoice.setPaymentReference(paymentReference);
        invoiceRepository.save(invoice);

        // Reflect payment on the order (already PAID for fulfilment; stamp paidAt when it lands).
        orderRepository.findById(invoice.getOrderId()).ifPresent(order -> {
            if (order.getPaidAt() == null) {
                order.setPaidAt(Instant.now());
                orderRepository.save(order);
            }
        });
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<B2BInvoiceResponse> listInvoices(UUID companyId, UUID ownerId, InvoiceStatus status,
                                                          int page, int size) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_QUOTES);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<B2BInvoice> result = status != null
                ? invoiceRepository.findByVendorCompanyIdAndStatus(companyId, status, pageable)
                : invoiceRepository.findByVendorCompanyId(companyId, pageable);
        return new PagedResponse<>(result.map(B2BInvoiceResponse::from));
    }

    @Override
    @Transactional(readOnly = true)
    public List<B2BInvoiceResponse> getOverdueInvoices(UUID companyId, UUID ownerId) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_QUOTES);
        Instant now = Instant.now();
        return invoiceRepository
                .findByVendorCompanyIdAndStatusIn(companyId, List.of(InvoiceStatus.ISSUED, InvoiceStatus.OVERDUE))
                .stream()
                .filter(i -> i.getDueDateAt() != null && i.getDueDateAt().isBefore(now))
                .map(B2BInvoiceResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public int markOverdueInvoices() {
        List<B2BInvoice> overdue = invoiceRepository.findByStatusAndDueDateAtBefore(
                InvoiceStatus.ISSUED, Instant.now());
        for (B2BInvoice inv : overdue) {
            inv.setStatus(InvoiceStatus.OVERDUE);
            invoiceRepository.save(inv);
        }
        if (!overdue.isEmpty()) {
            log.info("[B2B] Marked {} invoice(s) overdue", overdue.size());
        }
        return overdue.size();
    }

    private String generateInvoiceNumber() {
        // e.g. INV-20260620-3F9A1C7B — date prefix for readability + 8 hex chars of UUID entropy
        // (~4.3B values) so same-day issuance does not realistically collide on the unique index.
        // (A 4-digit random suffix had only ~9k values/day and was birthday-collision-prone.)
        String date = LocalDate.now(ZoneOffset.UTC).toString().replace("-", "");
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "INV-" + date + "-" + suffix;
    }
}
