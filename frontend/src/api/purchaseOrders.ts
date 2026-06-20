import api from '../api';
import type {
  Supplier,
  PurchaseOrder,
  PagedResponse,
  CreateSupplierPayload,
  UpdateSupplierPayload,
  CreatePOPayload,
  ReceiveItemsPayload,
} from '../types/purchaseOrders';
import type { POStatus } from '../types/purchaseOrders';

export const listSuppliers = (
  companyId: string,
  page = 0,
  size = 100,
): Promise<PagedResponse<Supplier>> => {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  return api
    .get<PagedResponse<Supplier>>(`/companies/${companyId}/suppliers?${params}`)
    .then((r) => r.data);
};

export const createSupplier = (
  companyId: string,
  payload: CreateSupplierPayload,
): Promise<Supplier> =>
  api.post<Supplier>(`/companies/${companyId}/suppliers`, payload).then((r) => r.data);

export const updateSupplier = (
  companyId: string,
  supplierId: string,
  payload: UpdateSupplierPayload,
): Promise<Supplier> =>
  api
    .patch<Supplier>(`/companies/${companyId}/suppliers/${supplierId}`, payload)
    .then((r) => r.data);

export const deleteSupplier = (companyId: string, supplierId: string): Promise<void> =>
  api.delete(`/companies/${companyId}/suppliers/${supplierId}`).then(() => undefined);

export const listPOs = (
  companyId: string,
  status?: POStatus,
  page = 0,
  size = 20,
): Promise<PagedResponse<PurchaseOrder>> => {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (status) params.set('status', status);
  return api
    .get<PagedResponse<PurchaseOrder>>(`/companies/${companyId}/purchase-orders?${params}`)
    .then((r) => r.data);
};

export const getPO = (companyId: string, poId: string): Promise<PurchaseOrder> =>
  api.get<PurchaseOrder>(`/companies/${companyId}/purchase-orders/${poId}`).then((r) => r.data);

export const createPO = (
  companyId: string,
  payload: CreatePOPayload,
): Promise<PurchaseOrder> =>
  api
    .post<PurchaseOrder>(`/companies/${companyId}/purchase-orders`, payload)
    .then((r) => r.data);

export const sendPO = (companyId: string, poId: string): Promise<PurchaseOrder> =>
  api
    .post<PurchaseOrder>(`/companies/${companyId}/purchase-orders/${poId}/send`)
    .then((r) => r.data);

export const confirmPO = (companyId: string, poId: string): Promise<PurchaseOrder> =>
  api
    .post<PurchaseOrder>(`/companies/${companyId}/purchase-orders/${poId}/confirm`)
    .then((r) => r.data);

export const receiveItems = (
  companyId: string,
  poId: string,
  payload: ReceiveItemsPayload,
): Promise<PurchaseOrder> =>
  api
    .post<PurchaseOrder>(`/companies/${companyId}/purchase-orders/${poId}/receive`, payload)
    .then((r) => r.data);

export const cancelPO = (companyId: string, poId: string): Promise<PurchaseOrder> =>
  api
    .post<PurchaseOrder>(`/companies/${companyId}/purchase-orders/${poId}/cancel`)
    .then((r) => r.data);
