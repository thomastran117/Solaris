import api from "../api";
import type { Feedback, FeedbackCategory, FeedbackStatus } from "../types/feedback";

interface PagedResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export interface SubmitFeedbackPayload {
  category: FeedbackCategory;
  message: string;
  rating?: number | null;
  pageContext?: string;
}

export interface AdminFeedbackListParams {
  status?: FeedbackStatus;
  category?: FeedbackCategory;
  page?: number;
  size?: number;
}

export const feedbackApi = {
  submit: (payload: SubmitFeedbackPayload) =>
    api.post<Feedback>("/feedback", payload),

  getMine: (page = 0, size = 20) =>
    api.get<PagedResponse<Feedback>>("/feedback/mine", { params: { page, size } }),
};

export const feedbackAdminApi = {
  list: (params: AdminFeedbackListParams = {}) =>
    api.get<PagedResponse<Feedback>>("/admin/feedback", { params }),

  updateStatus: (id: string, status: FeedbackStatus) =>
    api.patch<Feedback>(`/admin/feedback/${id}/status`, { status }),
};
