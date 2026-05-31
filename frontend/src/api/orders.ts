import api from "../api";
import type { DriverLocation, Order, OrderStatusHistoryEntry, PagedOrders, TrackingEvent, OrderStatus } from "../types/order";

export interface ShipOrderRequest {
  trackingNumber: string;
  carrier?: string;
  note?: string;
  itemIds?: string[];
}

export const ordersApi = {
  create: (body: Record<string, unknown>) =>
    // A per-request UUID prevents the backend creating a duplicate order if the
    // user double-submits or the client retries after a network timeout.
    api.post<Order>("/orders", body, {
      headers: { "Idempotency-Key": crypto.randomUUID() },
    }),

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

  getHistory: (orderId: string) =>
    api.get<OrderStatusHistoryEntry[]>(`/orders/${orderId}/history`),

  getDriverLocation: (orderId: string) =>
    api.get<DriverLocation | null>(`/orders/${orderId}/driver-location`),
};
