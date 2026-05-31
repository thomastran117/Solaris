import api from "../api";
import type { Report, ReportReason, ReportStatus, ReportTargetType } from "../types/report";

interface PresignResponse {
  uploadUrl: string;
  fileUrl: string;
  key: string;
  expiresIn: number;
}

interface PagedResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export interface CreateReportPayload {
  targetType: ReportTargetType;
  targetId: string;
  reason: ReportReason;
  title: string;
  description: string;
  screenshotUrls?: string[];
}

export interface AdminReportSearchParams {
  q?: string;
  targetType?: ReportTargetType;
  status?: ReportStatus;
  reason?: ReportReason;
  after?: string;
  before?: string;
  sort?: "newest" | "oldest" | "status";
  page?: number;
  size?: number;
}

export const reportsApi = {
  create: (payload: CreateReportPayload) =>
    api.post<{ id: string }>("/reports", payload),

  presignScreenshot: (contentType: string) =>
    api.post<PresignResponse>("/upload/presign", { folder: "REPORT_SCREENSHOT", contentType }),
};

export const adminReportsApi = {
  list: (params: { targetType?: ReportTargetType; status?: ReportStatus; page?: number; size?: number } = {}) =>
    api.get<PagedResponse<Report>>("/admin/reports", { params }),

  get: (reportId: string) =>
    api.get<Report>(`/admin/reports/${reportId}`),

  resolve: (reportId: string, resolution: ReportStatus) =>
    api.patch<void>(`/admin/reports/${reportId}`, { resolution }),

  search: (params: AdminReportSearchParams = {}) =>
    api.get<PagedResponse<Report>>("/admin/reports/search", { params }),

  reindex: () =>
    api.post<{ message: string }>("/admin/reports/reindex"),
};
