import api from "../api";
import type { DriverLocation, Order, OrderStatusHistoryEntry, PagedOrders, TrackingEvent, OrderStatus } from "../types/order";

export interface ShipOrderRequest {
  trackingNumber: string;
  carrier?: string;
  note?: string;
  itemIds?: string[];
}

export const ordersApi = {
  /**
   * @param idempotencyKey A UUID generated once per checkout intent and reused on retries.
   *   Generate this with `crypto.randomUUID()` when the user first initiates checkout, store it
   *   in a ref or state, and pass the same value on any retry so the backend deduplicates the request.
   */
  create: (body: Record<string, unknown>, idempotencyKey: string) =>
    api.post<Order>("/orders", body, {
      headers: { "Idempotency-Key": idempotencyKey },
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
