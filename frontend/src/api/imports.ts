import api from "../api";
import type {
  AttachImageItem,
  ImportDownload,
  ImportJob,
  ImportJobRowsPage,
  ImportJobsPage,
  ImportJobType,
  ImportMode,
} from "../types/imports";

export interface PresignResponse {
  uploadUrl: string;
  fileUrl: string;
  key: string;
  expiresIn: number;
}

export interface CreateImportJobPayload {
  jobType: ImportJobType;
  mode?: ImportMode;
  csvS3Key: string;
  fileName?: string;
}

export const importsApi = {
  presignCsv: (contentType: string) =>
    api.post<PresignResponse>("/upload/presign", { folder: "IMPORT_CSV", contentType }),

  presignImage: (contentType: string) =>
    api.post<PresignResponse>("/upload/presign", { folder: "PRODUCT_IMAGE", contentType }),

  create: (companyId: string, payload: CreateImportJobPayload) =>
    api.post<ImportJob>(`/companies/${companyId}/imports`, payload),

  get: (companyId: string, jobId: string) =>
    api.get<ImportJob>(`/companies/${companyId}/imports/${jobId}`),

  list: (companyId: string, params: { page?: number; size?: number } = {}) =>
    api.get<ImportJobsPage>(`/companies/${companyId}/imports`, { params }),

  errors: (companyId: string, jobId: string, params: { page?: number; size?: number } = {}) =>
    api.get<ImportJobRowsPage>(`/companies/${companyId}/imports/${jobId}/errors`, { params }),

  errorReport: (companyId: string, jobId: string) =>
    api.get<ImportDownload>(`/companies/${companyId}/imports/${jobId}/error-report`),

  export: (companyId: string) =>
    api.post<ImportDownload>(`/companies/${companyId}/imports/export`),

  attachImages: (companyId: string, items: AttachImageItem[]) =>
    api.post<{ attached: number }>(`/companies/${companyId}/imports/attach-images`, { items }),
};
