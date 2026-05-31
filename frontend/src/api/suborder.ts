import api from "../api";

export interface SubOrderItem {
  id: string;
  productId: string | null;
  productName: string;
  variantId: string | null;
  variantTitle: string | null;
  variantSku: string | null;
  quantity: number;
  unitPrice: number;
  fulfillmentLocationId: string | null;
  fulfillmentLocationName: string | null;
  fulfillmentStatus: string;
  bundleId: string | null;
  bundleName: string | null;
  discountAmount: number;
}

export interface SubOrder {
  id: string;
  orderId: string;
  marketplaceVendorId: string;
  marketplaceId: string;
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
  id: string;
  subOrderId: string;
  vendorId: string;
  marketplaceId: string;
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
  list: (vendorId: string, status?: string, page = 0, size = 20) =>
    api.get<SubOrder[]>(`/vendors/${vendorId}/sub-orders`, {
      params: { status, page, size },
    }),

  get: (vendorId: string, subOrderId: string) =>
    api.get<SubOrder>(`/vendors/${vendorId}/sub-orders/${subOrderId}`),

  pack: (vendorId: string, subOrderId: string) =>
    api.post<SubOrder>(`/vendors/${vendorId}/sub-orders/${subOrderId}/pack`),

  ship: (vendorId: string, subOrderId: string, data: ShipSubOrderRequest) =>
    api.post<SubOrder>(`/vendors/${vendorId}/sub-orders/${subOrderId}/ship`, data),

  deliver: (vendorId: string, subOrderId: string) =>
    api.post<SubOrder>(`/vendors/${vendorId}/sub-orders/${subOrderId}/deliver`),

  cancel: (vendorId: string, subOrderId: string, data: CancelSubOrderRequest) =>
    api.post<SubOrder>(`/vendors/${vendorId}/sub-orders/${subOrderId}/cancel`, data),

  getCommission: (vendorId: string, subOrderId: string) =>
    api.get<CommissionRecord>(`/vendors/${vendorId}/sub-orders/${subOrderId}/commission`),
};
