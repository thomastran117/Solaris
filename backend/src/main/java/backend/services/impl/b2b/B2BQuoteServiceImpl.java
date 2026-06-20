package backend.services.impl.b2b;

import backend.dtos.requests.b2b.CreateQuoteRequest;
import backend.dtos.requests.b2b.QuoteLineItemRequest;
import backend.dtos.requests.b2b.RevisedQuoteItemRequest;
import backend.dtos.requests.b2b.VendorQuoteResponseRequest;
import backend.dtos.responses.b2b.QuoteResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.dtos.responses.order.OrderResponse;
import backend.exceptions.http.BadRequestException;
import backend.exceptions.http.ConflictException;
import backend.exceptions.http.ResourceNotFoundException;
import backend.models.core.B2BAccount;
import backend.models.core.B2BQuote;
import backend.models.core.B2BQuoteItem;
import backend.models.core.Company;
import backend.models.core.Product;
import backend.models.core.ProductVariant;
import backend.models.core.User;
import backend.models.enums.CompanyCapability;
import backend.models.enums.QuoteStatus;
import backend.repositories.B2BAccountRepository;
import backend.repositories.B2BInvoiceRepository;
import backend.repositories.B2BQuoteRepository;
import backend.repositories.CompanyRepository;
import backend.repositories.ProductRepository;
import backend.repositories.ProductVariantRepository;
import backend.repositories.UserRepository;
import backend.services.intf.b2b.B2BInvoiceService;
import backend.services.intf.b2b.B2BQuoteService;
import backend.services.intf.company.CompanyAccessService;
import backend.services.intf.orders.OrderService;
import backend.services.intf.orders.QuoteOrderSpec;
import backend.services.intf.support.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class B2BQuoteServiceImpl implements B2BQuoteService {

    private static final Logger log = LoggerFactory.getLogger(B2BQuoteServiceImpl.class);

    private final B2BQuoteRepository quoteRepository;
    private final B2BAccountRepository accountRepository;
    private final B2BInvoiceRepository invoiceRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final CompanyAccessService companyAccessService;
    private final OrderService orderService;
    private final B2BInvoiceService invoiceService;
    private final EmailService emailService;

    private final int quoteExpiryDays;

    public B2BQuoteServiceImpl(B2BQuoteRepository quoteRepository,
                               B2BAccountRepository accountRepository,
                               B2BInvoiceRepository invoiceRepository,
                               ProductRepository productRepository,
                               ProductVariantRepository variantRepository,
                               CompanyRepository companyRepository,
                               UserRepository userRepository,
                               CompanyAccessService companyAccessService,
                               OrderService orderService,
                               B2BInvoiceService invoiceService,
                               EmailService emailService,
                               @Value("${app.b2b.quote-expiry-days:14}") int quoteExpiryDays) {
        this.quoteRepository = quoteRepository;
        this.accountRepository = accountRepository;
        this.invoiceRepository = invoiceRepository;
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.companyAccessService = companyAccessService;
        this.orderService = orderService;
        this.invoiceService = invoiceService;
        this.emailService = emailService;
        this.quoteExpiryDays = quoteExpiryDays;
    }

    @Override
    @Transactional
    public QuoteResponse requestQuote(UUID buyerUserId, UUID vendorCompanyId, CreateQuoteRequest req) {
        Company vendor = companyRepository.findById(vendorCompanyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found with id: " + vendorCompanyId));

        B2BAccount account = upsertAccount(buyerUserId, req);

        B2BQuote quote = new B2BQuote();
        quote.setVendorCompanyId(vendorCompanyId);
        quote.setBuyerUserId(buyerUserId);
        quote.setStatus(QuoteStatus.PENDING_VENDOR);
        quote.setPaymentTerms(req.paymentTerms());
        quote.setBuyerMessage(req.message());
        quote.setExpiresAt(Instant.now().plus(quoteExpiryDays, ChronoUnit.DAYS));

        for (QuoteLineItemRequest line : req.items()) {
            Product product = loadVendorProduct(line.productId(), vendorCompanyId);
            B2BQuoteItem item = new B2BQuoteItem();
            item.setQuote(quote);
            item.setProductId(product.getId());
            item.setProductName(product.getName());
            item.setQuantity(line.quantity());
            item.setUnitPriceCents(snapshotUnitPriceCents(product, line.variantId()));
            if (line.variantId() != null) {
                item.setVariantId(line.variantId());
            }
            item.recomputeTotal();
            quote.getItems().add(item);
        }

        B2BQuote saved = quoteRepository.save(quote);

        // Notify the vendor owner (best-effort, after commit).
        try {
            User owner = vendor.getOwner();
            if (owner != null) {
                emailService.sendQuoteReceivedEmail(owner.getEmail(), owner.getFirstName(),
                        account.getCompanyName(), saved.getId(), saved.totalCents());
            }
        } catch (Exception e) {
            log.warn("[B2B] Failed to queue quote-received email for quote {}: {}", saved.getId(), e.getMessage());
        }

        return QuoteResponse.from(saved);
    }

    @Override
    @Transactional
    public QuoteResponse vendorRespondToQuote(UUID companyId, UUID quoteId, UUID ownerId,
                                              VendorQuoteResponseRequest req) {
        Company vendor = companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_QUOTES);

        B2BQuote quote = quoteRepository.findByIdAndVendorCompanyId(quoteId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Quote not found with id: " + quoteId));

        if (quote.getStatus() != QuoteStatus.PENDING_VENDOR) {
            throw new ConflictException("Quote is not awaiting a vendor response");
        }

        if (req.paymentTerms() != null) {
            quote.setPaymentTerms(req.paymentTerms());
        }
        quote.setVendorNote(req.vendorNote());

        if (req.action() == VendorQuoteResponseRequest.VendorQuoteAction.COUNTER) {
            // orphanRemoval deletes the previous lines; rebuild from the revised pricing.
            quote.getItems().clear();
            for (RevisedQuoteItemRequest line : req.items()) {
                Product product = loadVendorProduct(line.productId(), companyId);
                B2BQuoteItem item = new B2BQuoteItem();
                item.setQuote(quote);
                item.setProductId(product.getId());
                item.setProductName(product.getName());
                item.setQuantity(line.quantity());
                item.setVariantId(line.variantId());
                item.setUnitPriceCents(line.unitPriceCents());
                item.recomputeTotal();
                quote.getItems().add(item);
            }
        }

        quote.setStatus(QuoteStatus.PENDING_BUYER);
        B2BQuote saved = quoteRepository.save(quote);

        try {
            User buyer = userRepository.findById(quote.getBuyerUserId()).orElse(null);
            if (buyer != null) {
                String responseStatus = req.action() == VendorQuoteResponseRequest.VendorQuoteAction.COUNTER
                        ? "COUNTERED" : "APPROVED";
                emailService.sendQuoteRespondedEmail(buyer.getEmail(), buyer.getFirstName(),
                        vendor.getName(), saved.getId(), responseStatus);
            }
        } catch (Exception e) {
            log.warn("[B2B] Failed to queue quote-responded email for quote {}: {}", saved.getId(), e.getMessage());
        }

        return QuoteResponse.from(saved);
    }

    @Override
    public OrderResponse buyerAcceptQuote(UUID quoteId, UUID buyerUserId) {
        // Not @Transactional: createOrderFromQuote performs its own two-phase tx with a Stripe call
        // that must not run inside an open transaction.
        B2BQuote quote = quoteRepository.findByIdAndBuyerUserId(quoteId, buyerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Quote not found with id: " + quoteId));

        if (quote.getStatus() != QuoteStatus.PENDING_BUYER) {
            throw new ConflictException("Quote is not awaiting buyer acceptance");
        }
        if (quote.getExpiresAt() != null && quote.getExpiresAt().isBefore(Instant.now())) {
            throw new ConflictException("Quote has expired");
        }

        boolean netTerms = quote.getPaymentTerms().isNet();
        if (netTerms) {
            enforceCreditLimit(buyerUserId, quote.totalCents());
        }

        List<QuoteOrderSpec.QuoteOrderLine> lines = quote.getItems().stream()
                .map(i -> new QuoteOrderSpec.QuoteOrderLine(
                        i.getProductId(), i.getVariantId(), i.getQuantity(), i.getUnitPriceCents()))
                .toList();
        QuoteOrderSpec spec = new QuoteOrderSpec(buyerUserId, quoteId, null, !netTerms, lines);

        OrderResponse order = orderService.createOrderFromQuote(spec);

        if (netTerms) {
            invoiceService.issueInvoice(order.getId(), quoteId);
        }

        quote.setConvertedOrderId(order.getId());
        quote.setStatus(QuoteStatus.CONVERTED);
        quoteRepository.save(quote);

        return order;
    }

    @Override
    @Transactional
    public void buyerRejectQuote(UUID quoteId, UUID buyerUserId) {
        B2BQuote quote = quoteRepository.findByIdAndBuyerUserId(quoteId, buyerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Quote not found with id: " + quoteId));
        if (quote.getStatus() != QuoteStatus.PENDING_BUYER) {
            throw new ConflictException("Only a quote awaiting buyer acceptance can be rejected");
        }
        quote.setStatus(QuoteStatus.REJECTED);
        quoteRepository.save(quote);
    }

    @Override
    @Transactional(readOnly = true)
    public QuoteResponse getBuyerQuote(UUID quoteId, UUID buyerUserId) {
        B2BQuote quote = quoteRepository.findByIdAndBuyerUserId(quoteId, buyerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Quote not found with id: " + quoteId));
        return QuoteResponse.from(quote);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<QuoteResponse> listBuyerQuotes(UUID buyerUserId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<B2BQuote> result = quoteRepository.findByBuyerUserId(buyerUserId, pageable);
        return new PagedResponse<>(result.map(QuoteResponse::from));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<QuoteResponse> listVendorQuotes(UUID companyId, UUID ownerId, QuoteStatus status,
                                                         int page, int size) {
        companyAccessService.require(companyId, ownerId, CompanyCapability.MANAGE_QUOTES);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<B2BQuote> result = status != null
                ? quoteRepository.findByVendorCompanyIdAndStatus(companyId, status, pageable)
                : quoteRepository.findByVendorCompanyId(companyId, pageable);
        return new PagedResponse<>(result.map(QuoteResponse::from));
    }

    @Override
    @Transactional
    public void expireStaleQuotes() {
        List<B2BQuote> stale = quoteRepository.findByStatusAndExpiresAtBefore(QuoteStatus.PENDING_BUYER, Instant.now());
        for (B2BQuote quote : stale) {
            quote.setStatus(QuoteStatus.EXPIRED);
            quoteRepository.save(quote);
        }
        if (!stale.isEmpty()) {
            log.info("[B2B] Expired {} stale quote(s)", stale.size());
        }
    }

    // ─── helpers ───────────────────────────────────────────────────────────────

    private B2BAccount upsertAccount(UUID buyerUserId, CreateQuoteRequest req) {
        B2BAccount account = accountRepository.findByUserId(buyerUserId).orElseGet(B2BAccount::new);
        account.setUserId(buyerUserId);
        account.setCompanyName(req.companyName());
        account.setTaxId(req.taxId());
        account.setBillingAddress(req.billingAddress());
        return accountRepository.save(account);
    }

    /** Loads a product and verifies it is sold by the given vendor company. */
    private Product loadVendorProduct(UUID productId, UUID vendorCompanyId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + productId));
        if (product.getCompany() == null || !vendorCompanyId.equals(product.getCompany().getId())) {
            throw new BadRequestException("Product " + productId + " is not sold by this vendor");
        }
        return product;
    }

    /** Snapshots the current list price (variant price when given, else product price) in cents. */
    private long snapshotUnitPriceCents(Product product, UUID variantId) {
        BigDecimal price = product.getPrice();
        if (variantId != null) {
            ProductVariant variant = variantRepository.findById(variantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Variant not found with id: " + variantId));
            if (variant.getProduct() == null || !product.getId().equals(variant.getProduct().getId())) {
                throw new BadRequestException("Variant " + variantId + " does not belong to product " + product.getId());
            }
            if (variant.getPrice() != null) {
                price = variant.getPrice();
            }
        }
        if (price == null) {
            throw new BadRequestException("Product " + product.getId() + " has no price to quote");
        }
        return price.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private void enforceCreditLimit(UUID buyerUserId, long newOrderCents) {
        B2BAccount account = accountRepository.findByUserId(buyerUserId)
                .orElseThrow(() -> new ConflictException("Buyer is not approved for net payment terms"));
        if (!account.isNetTermsApproved()) {
            throw new ConflictException("Buyer is not approved for net payment terms");
        }
        long outstanding = invoiceRepository.sumOutstandingByBuyer(buyerUserId);
        if (outstanding + newOrderCents > account.getNetTermsLimitCents()) {
            throw new ConflictException("Net-terms credit limit exceeded");
        }
    }
}
