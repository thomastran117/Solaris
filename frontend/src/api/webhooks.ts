import api from "../api";
import type {
  WebhookCreationResponse,
  WebhookSubscriptionResponse,
  WebhookDeliveryPage,
  RegisterWebhookPayload,
} from "../types/webhook";

export const webhooksApi = {
  register: (companyId: string, payload: RegisterWebhookPayload) =>
    api.post<WebhookCreationResponse>(`/companies/${companyId}/webhooks`, payload),

  list: (companyId: string) =>
    api.get<WebhookSubscriptionResponse[]>(`/companies/${companyId}/webhooks`),

  remove: (companyId: string, id: string) =>
    api.delete(`/companies/${companyId}/webhooks/${id}`),

  verify: (companyId: string, id: string) =>
    api.post<void>(`/companies/${companyId}/webhooks/${id}/verify`),

  deliveries: (companyId: string, id: string, page = 0) =>
    api.get<WebhookDeliveryPage>(
      `/companies/${companyId}/webhooks/${id}/deliveries?page=${page}&size=20`
    ),
};
