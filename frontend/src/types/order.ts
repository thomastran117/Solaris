export type OrderStatus =
  | "RESERVED"
  | "PAID"
  | "PACKED"
  | "PARTIALLY_FULFILLED"
  | "SHIPPED"
  | "DELIVERED"
  | "RETURNED"
  | "FAILED"
  | "CANCELLED"
  | "REFUNDED"
  | "UNDER_REVIEW";

export type FulfillmentMethod = "DELIVERY" | "PICKUP";

export type FulfillmentStatus =
  | "PENDING"
  | "BACKORDERED"
  | "PREORDERED"
  | "PACKED"
  | "PICKUP_READY"
  | "SHIPPED"
  | "DELIVERED"
  | "RETURNED"
  | "CANCELLED";

export interface TrackingEvent {
  status: string | null;
  message: string | null;
  location: string | null;
  occurredAt: string | null;
}

export interface NearbyPickupLocation {
  id: string;
  name: string;
  code: string;
  address: string | null;
  city: string | null;
  country: string | null;
  distanceKm: number;
  pickupReadyHours: number | null;
  type: "STORE" | "HYBRID";
}

export interface OrderItemSummary {
  id: string;
  productId: string | null;
  productName: string;
  variantId: string | null;
  variantTitle: string | null;
  variantSku: string | null;
  quantity: number;
  unitPrice: number;
  discountAmount: number;
  fulfillmentStatus: FulfillmentStatus;
  fulfillmentMethod: FulfillmentMethod;
  fulfillmentLocationName: string | null;
  bundleName: string | null;
}

export interface ShippingAddress {
  recipientName: string | null;
  street: string | null;
  street2: string | null;
  city: string | null;
  state: string | null;
  postalCode: string | null;
  country: string | null;
  phoneNumber: string | null;
}

export interface Order {
  id: string;
  userId: string;
  items: OrderItemSummary[];
  totalAmount: number;
  currency: string;
  status: OrderStatus;
  fulfillmentMethod: FulfillmentMethod;
  trackingNumber: string | null;
  carrier: string | null;
  shippedAt: string | null;
  deliveredAt: string | null;
  returnedAt: string | null;
  fulfillmentNote: string | null;
  shipRecipientName: string | null;
  shipStreet: string | null;
  shipStreet2: string | null;
  shipCity: string | null;
  shipState: string | null;
  shipPostalCode: string | null;
  shipCountry: string | null;
  shipPhoneNumber: string | null;
  pickupLocationName: string | null;
  pickupReadyAt: string | null;
  couponCode: string | null;
  couponDiscountAmount: number;
  refundedAmountCents: number;
  createdAt: string;
  updatedAt: string;
}

export interface CompanyOrder {
  orderId: string;
  buyerUserId: string;
  orderStatus: OrderStatus;
  currency: string;
  companyItemsTotal: number;
  items: OrderItemSummary[];
  fulfillmentMethod: FulfillmentMethod;
  trackingNumber: string | null;
  carrier: string | null;
  shippedAt: string | null;
  deliveredAt: string | null;
  pickupLocationId: string | null;
  pickupLocationName: string | null;
  pickupReadyAt: string | null;
  shipRecipientName: string | null;
  shipStreet: string | null;
  shipStreet2: string | null;
  shipCity: string | null;
  shipState: string | null;
  shipPostalCode: string | null;
  shipCountry: string | null;
  createdAt: string;
}

export interface PagedOrders {
  content: Order[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export interface PagedCompanyOrders {
  content: CompanyOrder[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export interface OrderStatusHistoryEntry {
  id: string;
  eventType: string;
  status: string | null;
  occurredAt: string;
  actorId: string | null;
  note: string | null;
}

export interface DriverLocation {
  driverId: string;
  lat: number;
  lng: number;
  timestamp: string;
}

export interface SseStatusUpdateEvent {
  eventId: string;
  orderId: string;
  status: string;
  trackingNumber: string | null;
  carrier: string | null;
  driverLat: number | null;
  driverLng: number | null;
  note: string | null;
  occurredAt: string;
  eventType: string;
}
