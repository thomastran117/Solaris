import api from "../api";

export interface SubOrderItem {
  id: number;
  productId: number | null;
  productName: string;
  variantId: number | null;
  variantTitle: string | null;
  variantSku: string | null;
  quantity: number;
  unitPrice: number;
  fulfillmentLocationId: number | null;
  fulfillmentLocationName: string | null;
  fulfillmentStatus: string;
  bundleId: number | null;
  bundleName: string | null;
  discountAmount: number;
}

export interface SubOrder {
  id: number;
  orderId: number;
  marketplaceVendorId: number;
  marketplaceId: number;
  vendorCompanyName: string;
  status: string;
  subtotal: number;
  totalAmount: number;
  currency: string;
  commissionAmount: number | null;
  netVendorAmount: number | null;
  trackingNumber: string | null;
  carrier: string | null;
  fulfillmentNote: string | null;
  cancellationReason: string | null;
  paidAt: string | null;
  packedAt: string | null;
  shippedAt: string | null;
  deliveredAt: string | null;
  cancelledAt: string | null;
  createdAt: string;
  updatedAt: string;
  items: SubOrderItem[];
}

export interface CommissionRecord {
  id: number;
  subOrderId: number;
  vendorId: number;
  marketplaceId: number;
  commissionRate: number;
  grossAmount: number;
  commissionAmount: number;
  netVendorAmount: number;
  currency: string;
  computedAt: string;
}

export interface ShipSubOrderRequest {
  trackingNumber: string;
  carrier: string;
  fulfillmentNote?: string;
}

export interface CancelSubOrderRequest {
  reason: string;
}

export const subOrderApi = {
  list: (vendorId: number, status?: string, page = 0, size = 20) =>
    api.get<SubOrder[]>(`/vendors/${vendorId}/sub-orders`, {
      params: { status, page, size },
    }),

  get: (vendorId: number, subOrderId: number) =>
    api.get<SubOrder>(`/vendors/${vendorId}/sub-orders/${subOrderId}`),

  pack: (vendorId: number, subOrderId: number) =>
    api.post<SubOrder>(`/vendors/${vendorId}/sub-orders/${subOrderId}/pack`),

  ship: (vendorId: number, subOrderId: number, data: ShipSubOrderRequest) =>
    api.post<SubOrder>(`/vendors/${vendorId}/sub-orders/${subOrderId}/ship`, data),

  deliver: (vendorId: number, subOrderId: number) =>
    api.post<SubOrder>(`/vendors/${vendorId}/sub-orders/${subOrderId}/deliver`),

  cancel: (vendorId: number, subOrderId: number, data: CancelSubOrderRequest) =>
    api.post<SubOrder>(`/vendors/${vendorId}/sub-orders/${subOrderId}/cancel`, data),

  getCommission: (vendorId: number, subOrderId: number) =>
    api.get<CommissionRecord>(`/vendors/${vendorId}/sub-orders/${subOrderId}/commission`),
};
