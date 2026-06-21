export type QuoteStatus =
  | 'PENDING_VENDOR'
  | 'PENDING_BUYER'
  | 'REJECTED'
  | 'EXPIRED'
  | 'CONVERTED';

export type PaymentTerms = 'IMMEDIATE' | 'NET_30' | 'NET_60';

export type InvoiceStatus = 'ISSUED' | 'PAID' | 'OVERDUE' | 'CANCELLED';

export interface QuoteItem {
  id: string;
  productId: string;
  variantId: string | null;
  productName: string | null;
  quantity: number;
  unitPriceCents: number;
  totalPriceCents: number;
}

export interface Quote {
  id: string;
  vendorCompanyId: string;
  buyerUserId: string;
  status: QuoteStatus;
  expiresAt: string;
  buyerMessage: string | null;
  vendorNote: string | null;
  paymentTerms: PaymentTerms;
  currency: string;
  convertedOrderId: string | null;
  totalCents: number;
  items: QuoteItem[];
  createdAt: string;
  updatedAt: string;
}

export interface B2BInvoice {
  id: string;
  orderId: string;
  quoteId: string;
  vendorCompanyId: string;
  buyerUserId: string;
  invoiceNumber: string;
  dueDateAt: string;
  totalCents: number;
  status: InvoiceStatus;
  paidAt: string | null;
  paymentReference: string | null;
  createdAt: string;
}

export interface PagedResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export interface QuoteLineItemPayload {
  productId: string;
  variantId?: string;
  quantity: number;
}

export interface CreateQuotePayload {
  companyName: string;
  taxId?: string;
  billingAddress?: string;
  message?: string;
  paymentTerms: PaymentTerms;
  items: QuoteLineItemPayload[];
}

export interface RevisedQuoteItemPayload {
  productId: string;
  variantId?: string;
  quantity: number;
  unitPriceCents: number;
}

export interface VendorRespondPayload {
  action: 'APPROVE' | 'COUNTER';
  vendorNote?: string;
  paymentTerms?: PaymentTerms;
  items?: RevisedQuoteItemPayload[];
}
