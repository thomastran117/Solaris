export type TransferStatus = 'PENDING' | 'IN_TRANSIT' | 'RECEIVED' | 'CANCELLED';

export interface InventoryTransfer {
  id: string;
  companyId: string;
  productId: string;
  productName: string;
  fromLocationId: string;
  fromLocationName: string;
  toLocationId: string;
  toLocationName: string;
  quantity: number;
  status: TransferStatus;
  notes: string | null;
  createdByUserId: string;
  receivedByUserId: string | null;
  cancelledByUserId: string | null;
  createdAt: string;
  inTransitAt: string | null;
  receivedAt: string | null;
  cancelledAt: string | null;
}

export interface CreateTransferPayload {
  productId: string;
  fromLocationId: string;
  toLocationId: string;
  quantity: number;
  notes?: string;
}

/** Minimal shape of an inventory location, used to populate the source/destination dropdowns. */
export interface InventoryLocationOption {
  id: string;
  name: string;
  code: string;
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
