import api from "../api";

export interface VendorBalance {
  vendorId: number;
  pendingCents: number;
  availableCents: number;
  inTransitCents: number;
  lifetimeGrossCents: number;
  lifetimeCommissionCents: number;
  lifetimePaidOutCents: number;
  currency: string;
  updatedAt: string | null;
}

export interface VendorPayoutItem {
  id: number;
  subOrderId: number | null;
  commissionRecordId: number | null;
  adjustmentId: number | null;
  entryType: "SALE" | "REFUND" | "ADJUSTMENT" | "CHARGEBACK";
  grossAmount: number;
  commissionAmount: number;
  netAmount: number;
}

export interface VendorPayout {
  id: number;
  vendorId: number;
  marketplaceId: number;
  periodStart: string | null;
  periodEnd: string | null;
  grossAmount: number;
  commissionAmount: number;
  refundAmount: number;
  adjustmentAmount: number;
  netAmount: number;
  currency: string;
  status: "SCHEDULED" | "PROCESSING" | "PAID" | "FAILED" | "REVERSED";
  stripeTransferId: string | null;
  failureReason: string | null;
  scheduledAt: string | null;
  paidAt: string | null;
  createdAt: string;
  updatedAt: string;
  items: VendorPayoutItem[];
}

export interface VendorAdjustment {
  id: number;
  vendorId: number;
  amountCents: number;
  currency: string;
  reason: string;
  createdByUserId: string;
  appliedToPayoutId: number | null;
  createdAt: string;
}

export interface VendorAdjustmentRequest {
  amountCents: number;
  currency: string;
  reason: string;
}

export const payoutApi = {
  getBalance: (vendorId: number) =>
    api.get<VendorBalance>(`/vendors/${vendorId}/balance`),

  listPayouts: (vendorId: number, status?: string, page = 0, size = 20) =>
    api.get<VendorPayout[]>(`/vendors/${vendorId}/payouts`, {
      params: { status, page, size },
    }),

  getPayoutDetail: (vendorId: number, payoutId: number) =>
    api.get<VendorPayout>(`/vendors/${vendorId}/payouts/${payoutId}`),

  triggerManualPayout: (marketplaceId: number, vendorId: number) =>
    api.post<VendorPayout>(`/marketplaces/${marketplaceId}/payouts/run`, null, {
      params: { vendorId },
    }),

  createAdjustment: (
    marketplaceId: number,
    vendorId: number,
    data: VendorAdjustmentRequest
  ) =>
    api.post<VendorAdjustment>(
      `/marketplaces/${marketplaceId}/vendors/${vendorId}/adjustments`,
      data
    ),
};
