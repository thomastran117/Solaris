export type POStatus =
  | 'DRAFT'
  | 'SENT'
  | 'CONFIRMED'
  | 'PARTIALLY_RECEIVED'
  | 'RECEIVED'
  | 'CANCELLED';

export interface Supplier {
  id: string;
  companyId: string;
  name: string;
  email: string | null;
  phone: string | null;
  address: string | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PurchaseOrderItem {
  id: string;
  productId: string;
  productName: string;
  variantId: string | null;
  variantSku: string | null;
  orderedQty: number;
  receivedQty: number;
  unitCostCents: number;
  locationId: string | null;
  locationName: string | null;
}

export interface PurchaseOrder {
  id: string;
  companyId: string;
  supplierId: string;
  supplierName: string;
  status: POStatus;
  expectedArrivalDate: string | null;
  notes: string | null;
  totalCostCents: number;
  restockRequestId: string | null;
  createdByUserId: string;
  createdAt: string;
  sentAt: string | null;
  receivedAt: string | null;
  updatedAt: string;
  items: PurchaseOrderItem[];
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

export interface CreateSupplierPayload {
  name: string;
  email?: string;
  phone?: string;
  address?: string;
  notes?: string;
}

export interface UpdateSupplierPayload {
  name?: string;
  email?: string;
  phone?: string;
  address?: string;
  notes?: string;
}

export interface POLineItemPayload {
  productId: string;
  variantId?: string;
  orderedQty: number;
  unitCostCents: number;
  locationId?: string;
}

export interface CreatePOPayload {
  supplierId: string;
  expectedArrivalDate?: string;
  notes?: string;
  restockRequestId?: string;
  items: POLineItemPayload[];
}

export interface ReceiveLineItemPayload {
  itemId: string;
  receivedQty: number;
}

export interface ReceiveItemsPayload {
  items: ReceiveLineItemPayload[];
}
