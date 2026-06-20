package backend.services.intf.b2b;

import backend.dtos.responses.b2b.B2BInvoiceResponse;
import backend.dtos.responses.general.PagedResponse;
import backend.models.enums.InvoiceStatus;

import java.util.List;
import java.util.UUID;

/**
 * Net-terms invoicing for converted B2B orders (Feature 12). Payment is tracked here, separately
 * from the order (which is created PAID so it fulfils normally).
 */
public interface B2BInvoiceService {

    /** Issues an ISSUED invoice for a net-terms order, due {@code paymentTerms.netDays()} out. */
    B2BInvoiceResponse issueInvoice(UUID orderId, UUID quoteId);

    /** Vendor records an out-of-band payment; transitions ISSUED/OVERDUE → PAID. Requires MANAGE_QUOTES. */
    void markInvoicePaid(UUID companyId, UUID invoiceId, UUID ownerId, String paymentReference);

    PagedResponse<B2BInvoiceResponse> listInvoices(UUID companyId, UUID ownerId, InvoiceStatus status,
                                                   int page, int size);

    /** Invoices past their due date and still unpaid, for the vendor's overdue view. */
    List<B2BInvoiceResponse> getOverdueInvoices(UUID companyId, UUID ownerId);

    /** Scheduled: flips ISSUED invoices past their due date to OVERDUE. Returns the count updated. */
    int markOverdueInvoices();
}
