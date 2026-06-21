import type { AxiosResponse } from 'axios';
import api from '../api';
import type { ApiMeta } from '../types/api';
import type {
  Quote,
  B2BInvoice,
  PagedResponse,
  QuoteStatus,
  InvoiceStatus,
  CreateQuotePayload,
  VendorRespondPayload,
} from '../types/b2b';
import type { Order } from '../types/order';

/**
 * The backend returns a {@code PagedResponse} which the API envelope unwraps to `data` (the items
 * array) plus `meta` (pagination). Reassemble it for the page to consume.
 */
function toPaged<T>(r: AxiosResponse, page: number, size: number): PagedResponse<T> {
  const meta = (r as AxiosResponse & { meta?: ApiMeta | null }).meta ?? {};
  const items = (r.data as T[]) ?? [];
  return {
    items,
    page: meta.page ?? page,
    size: meta.size ?? size,
    totalElements: meta.totalElements ?? items.length,
    totalPages: meta.totalPages ?? 1,
    hasNext: meta.hasNext ?? false,
    hasPrevious: meta.hasPrevious ?? false,
  };
}

// ── Buyer ──────────────────────────────────────────────────────────────────────

export const requestQuote = (
  vendorCompanyId: string,
  payload: CreateQuotePayload,
): Promise<Quote> =>
  api
    .post<Quote>(`/b2b/quotes?vendorCompanyId=${vendorCompanyId}`, payload)
    .then((r) => r.data);

export const listMyQuotes = (
  status?: QuoteStatus,
  page = 0,
  size = 20,
): Promise<PagedResponse<Quote>> => {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (status) params.set('status', status);
  return api.get<Quote[]>(`/b2b/quotes?${params}`).then((r) => toPaged<Quote>(r, page, size));
};

export const getQuote = (id: string): Promise<Quote> =>
  api.get<Quote>(`/b2b/quotes/${id}`).then((r) => r.data);

export const acceptQuote = (id: string): Promise<Order> =>
  api.post<Order>(`/b2b/quotes/${id}/accept`).then((r) => r.data);

export const rejectQuote = (id: string): Promise<void> =>
  api.post(`/b2b/quotes/${id}/reject`).then(() => undefined);

// ── Vendor ─────────────────────────────────────────────────────────────────────

export const listInboundQuotes = (
  companyId: string,
  status?: QuoteStatus,
  page = 0,
  size = 20,
): Promise<PagedResponse<Quote>> => {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (status) params.set('status', status);
  return api
    .get<Quote[]>(`/companies/${companyId}/b2b/quotes?${params}`)
    .then((r) => toPaged<Quote>(r, page, size));
};

export const respondToQuote = (
  companyId: string,
  quoteId: string,
  payload: VendorRespondPayload,
): Promise<Quote> =>
  api
    .patch<Quote>(`/companies/${companyId}/b2b/quotes/${quoteId}/respond`, payload)
    .then((r) => r.data);

export const listInvoices = (
  companyId: string,
  status?: InvoiceStatus,
  page = 0,
  size = 20,
): Promise<PagedResponse<B2BInvoice>> => {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (status) params.set('status', status);
  return api
    .get<B2BInvoice[]>(`/companies/${companyId}/b2b/invoices?${params}`)
    .then((r) => toPaged<B2BInvoice>(r, page, size));
};

export const listOverdueInvoices = (companyId: string): Promise<B2BInvoice[]> =>
  api.get<B2BInvoice[]>(`/companies/${companyId}/b2b/invoices/overdue`).then((r) => r.data);

export const markInvoicePaid = (
  companyId: string,
  invoiceId: string,
  paymentReference?: string,
): Promise<void> =>
  api
    .post(`/companies/${companyId}/b2b/invoices/${invoiceId}/mark-paid`, { paymentReference })
    .then(() => undefined);
