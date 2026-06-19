import api from '../api';
import type {
  MarketingWorkflow,
  WorkflowAnalytics,
  CreateWorkflowPayload,
  UpdateWorkflowPayload,
  SpringPage,
} from '../types/marketing';

export const listWorkflows = (
  companyId: string,
  page = 0,
  size = 50,
): Promise<SpringPage<MarketingWorkflow>> =>
  api
    .get<SpringPage<MarketingWorkflow>>(
      `/companies/${companyId}/marketing/workflows?page=${page}&size=${size}`,
    )
    .then((r) => r.data);

export const createWorkflow = (
  companyId: string,
  payload: CreateWorkflowPayload,
): Promise<MarketingWorkflow> =>
  api.post<MarketingWorkflow>(`/companies/${companyId}/marketing/workflows`, payload).then((r) => r.data);

export const updateWorkflow = (
  companyId: string,
  workflowId: string,
  payload: UpdateWorkflowPayload,
): Promise<MarketingWorkflow> =>
  api
    .patch<MarketingWorkflow>(`/companies/${companyId}/marketing/workflows/${workflowId}`, payload)
    .then((r) => r.data);

export const getWorkflowAnalytics = (
  companyId: string,
  workflowId: string,
): Promise<WorkflowAnalytics> =>
  api
    .get<WorkflowAnalytics>(`/companies/${companyId}/marketing/workflows/${workflowId}/analytics`)
    .then((r) => r.data);
