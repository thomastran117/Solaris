import api from "../api";
import type { Order, PagedOrders, TrackingEvent, OrderStatus } from "../types/order";

export interface ShipOrderRequest {
  trackingNumber: string;
  carrier?: string;
  note?: string;
  itemIds?: string[];
}

export const ordersApi = {
  list: (params?: { status?: OrderStatus; page?: number; size?: number }) =>
    api.get<PagedOrders>("/orders", { params }),

  get: (orderId: string) =>
    api.get<Order>(`/orders/${orderId}`),

  getLatest: () =>
    api.get<Order>("/orders/latest"),

  cancel: (orderId: string) =>
    api.post<Order>(`/orders/${orderId}/cancel`),

  reorder: (orderId: string) =>
    api.post<Order>(`/orders/${orderId}/reorder`),

  getTracking: (orderId: string) =>
    api.get<TrackingEvent[]>(`/orders/${orderId}/tracking`),
};
