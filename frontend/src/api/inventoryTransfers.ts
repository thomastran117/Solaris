import type { AxiosResponse } from 'axios';
import api from '../api';
import type { ApiMeta } from '../types/api';
import type {
  InventoryTransfer,
  InventoryLocationOption,
  CreateTransferPayload,
  PagedResponse,
  TransferStatus,
} from '../types/inventoryTransfers';

const base = (companyId: string) => `/companies/${companyId}/inventory/transfers`;

/**
 * The backend returns a {@code PagedResponse} which the API envelope unwraps to `data` (the items
 * array) plus `meta` (pagination). Reassemble it into a PagedResponse for the page to consume.
 */
export const listTransfers = (
  companyId: string,
  status?: TransferStatus,
  locationId?: string,
  page = 0,
  size = 20,
): Promise<PagedResponse<InventoryTransfer>> => {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (status) params.set('status', status);
  if (locationId) params.set('locationId', locationId);
  return api
    .get<InventoryTransfer[]>(`${base(companyId)}?${params}`)
    .then((r) => {
      const meta = (r as AxiosResponse & { meta?: ApiMeta | null }).meta ?? {};
      const items = (r.data as InventoryTransfer[]) ?? [];
      return {
        items,
        page: meta.page ?? page,
        size: meta.size ?? size,
        totalElements: meta.totalElements ?? items.length,
        totalPages: meta.totalPages ?? 1,
        hasNext: meta.hasNext ?? false,
        hasPrevious: meta.hasPrevious ?? false,
      };
    });
};

export const getTransfer = (companyId: string, transferId: string): Promise<InventoryTransfer> =>
  api.get<InventoryTransfer>(`${base(companyId)}/${transferId}`).then((r) => r.data);

export const createTransfer = (
  companyId: string,
  payload: CreateTransferPayload,
): Promise<InventoryTransfer> =>
  api.post<InventoryTransfer>(base(companyId), payload).then((r) => r.data);

export const dispatchTransfer = (companyId: string, transferId: string): Promise<InventoryTransfer> =>
  api.post<InventoryTransfer>(`${base(companyId)}/${transferId}/dispatch`).then((r) => r.data);

export const receiveTransfer = (companyId: string, transferId: string): Promise<InventoryTransfer> =>
  api.post<InventoryTransfer>(`${base(companyId)}/${transferId}/receive`).then((r) => r.data);

export const cancelTransfer = (companyId: string, transferId: string): Promise<InventoryTransfer> =>
  api.post<InventoryTransfer>(`${base(companyId)}/${transferId}/cancel`).then((r) => r.data);

/** Lists the company's inventory locations to populate the source/destination dropdowns. */
export const listInventoryLocations = (companyId: string): Promise<InventoryLocationOption[]> =>
  api
    .get<InventoryLocationOption[]>(`/companies/${companyId}/inventory/locations`)
    .then((r) => r.data);
