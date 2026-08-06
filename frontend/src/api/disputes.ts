import type { AxiosResponse } from 'axios';
import api from '../api';
import type { ApiMeta } from '../types/api';
import type {
  AddEvidencePayload,
  DisputeCase,
  DisputeCaseDetail,
  DisputeEvidence,
  PagedResponse,
} from '../types/disputes';

const base = '/admin/disputes';

/**
 * The backend returns a {@code PagedResponse} which the API envelope unwraps to `data` (the items
 * array) plus `meta` (pagination). Reassemble it into a PagedResponse for the page to consume.
 */
export const listOpenDisputes = (page = 0, size = 20): Promise<PagedResponse<DisputeCase>> => {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  return api.get<DisputeCase[]>(`${base}?${params}`).then((r) => {
    const meta = (r as AxiosResponse & { meta?: ApiMeta | null }).meta ?? {};
    const items = (r.data as DisputeCase[]) ?? [];
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

export const getDispute = (disputeId: string): Promise<DisputeCaseDetail> =>
  api.get<DisputeCaseDetail>(`${base}/${disputeId}`).then((r) => r.data);

export const addEvidence = (
  disputeId: string,
  payload: AddEvidencePayload,
): Promise<DisputeEvidence> =>
  api.post<DisputeEvidence>(`${base}/${disputeId}/evidence`, payload).then((r) => r.data);

/** Staff-only view of the chargebacks raised against a single order. */
export const listOrderDisputes = (orderId: string): Promise<DisputeCase[]> =>
  api.get<DisputeCase[]>(`/orders/${orderId}/disputes`).then((r) => r.data);
