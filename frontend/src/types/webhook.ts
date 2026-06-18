export type WebhookEventType =
  | "ORDER_CREATED"
  | "ORDER_PAID"
  | "ORDER_SHIPPED"
  | "ORDER_CANCELLED"
  | "ORDER_DELIVERED"
  | "STOCK_LOW";

export type WebhookSubscriptionStatus =
  | "PENDING_VERIFICATION"
  | "ACTIVE"
  | "DISABLED";

export type WebhookDeliveryStatus = "PENDING" | "DELIVERED" | "FAILED";

export interface WebhookSubscriptionResponse {
  id: string;
  url: string;
  events: WebhookEventType[];
  status: WebhookSubscriptionStatus;
  createdAt: string;
  updatedAt: string;
}

export interface WebhookCreationResponse {
  subscription: WebhookSubscriptionResponse;
  secret: string;
}

export interface WebhookDeliveryLogResponse {
  id: string;
  subscriptionId: string;
  eventType: WebhookEventType;
  responseStatus: number | null;
  attemptCount: number;
  deliveredAt: string | null;
  status: WebhookDeliveryStatus;
  createdAt: string;
}

export interface RegisterWebhookPayload {
  url: string;
  events: WebhookEventType[];
}

export interface WebhookDeliveryPage {
  items: WebhookDeliveryLogResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}
