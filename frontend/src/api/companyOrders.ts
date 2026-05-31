import api from "../api";
import type { CompanyOrder, PagedCompanyOrders, OrderStatus } from "../types/order";

export interface ShipOrderRequest {
  trackingNumber: string;
  carrier?: string;
  note?: string;
  itemIds?: string[];
}

export const companyOrdersApi = {
  list: (companyId: string, params?: { status?: OrderStatus; page?: number; size?: number }) =>
    api.get<PagedCompanyOrders>(`/companies/${companyId}/orders`, { params }),

  get: (companyId: string, orderId: string) =>
    api.get<CompanyOrder>(`/companies/${companyId}/orders/${orderId}`),

  pack: (companyId: string, orderId: string) =>
    api.post<CompanyOrder>(`/companies/${companyId}/orders/${orderId}/pack`),

  ship: (companyId: string, orderId: string, data: ShipOrderRequest) =>
    api.post<CompanyOrder>(`/companies/${companyId}/orders/${orderId}/ship`, data),

  markPickupReady: (companyId: string, orderId: string) =>
    api.post<CompanyOrder>(`/companies/${companyId}/orders/${orderId}/pickup-ready`),

  deliver: (companyId: string, orderId: string) =>
    api.post<CompanyOrder>(`/companies/${companyId}/orders/${orderId}/deliver`),

  cancel: (companyId: string, orderId: string) =>
    api.post<CompanyOrder>(`/companies/${companyId}/orders/${orderId}/cancel`),

  assignDriver: (companyId: string, orderId: string, driverUserId: string) =>
    api.post<CompanyOrder>(`/companies/${companyId}/orders/${orderId}/assign-driver`, { driverUserId }),
};
