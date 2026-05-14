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

  create: (companyId: number, payload: CreateImportJobPayload) =>
    api.post<ImportJob>(`/companies/${companyId}/imports`, payload),

  get: (companyId: number, jobId: number) =>
    api.get<ImportJob>(`/companies/${companyId}/imports/${jobId}`),

  list: (companyId: number, params: { page?: number; size?: number } = {}) =>
    api.get<ImportJobsPage>(`/companies/${companyId}/imports`, { params }),

  errors: (companyId: number, jobId: number, params: { page?: number; size?: number } = {}) =>
    api.get<ImportJobRowsPage>(`/companies/${companyId}/imports/${jobId}/errors`, { params }),

  errorReport: (companyId: number, jobId: number) =>
    api.get<ImportDownload>(`/companies/${companyId}/imports/${jobId}/error-report`),

  export: (companyId: number) =>
    api.post<ImportDownload>(`/companies/${companyId}/imports/export`),

  attachImages: (companyId: number, items: AttachImageItem[]) =>
    api.post<{ attached: number }>(`/companies/${companyId}/imports/attach-images`, { items }),
};
