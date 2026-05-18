export type ImportJobType = "PRODUCT_UPSERT" | "INVENTORY_SYNC" | "EXPORT";
export type ImportMode = "UPSERT" | "CREATE_ONLY" | "UPDATE_ONLY";
export type ImportJobStatus =
  | "PENDING"
  | "PARSING"
  | "VALIDATING"
  | "PROCESSING"
  | "COMPLETED"
  | "COMPLETED_WITH_ERRORS"
  | "FAILED";

export const TERMINAL_STATUSES: ReadonlyArray<ImportJobStatus> = [
  "COMPLETED",
  "COMPLETED_WITH_ERRORS",
  "FAILED",
];

export interface ImportJob {
  id: number;
  companyId: number;
  uploadedBy: string;
  jobType: ImportJobType;
  mode: ImportMode;
  status: ImportJobStatus;
  fileName: string | null;
  totalRows: number;
  processedRows: number;
  successRows: number;
  errorRows: number;
  progressPercent: number;
  hasErrorReport: boolean;
  failureReason: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ImportJobRow {
  id: number;
  jobId: number;
  rowNumber: number;
  sku: string | null;
  errorMessage: string;
}

export interface ImportJobsPage {
  items: ImportJob[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export interface ImportJobRowsPage {
  items: ImportJobRow[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export interface ImportDownload {
  url: string;
  expiresIn: number;
}

export interface AttachImageItem {
  sku: string;
  imageUrl: string;
  displayOrder: number;
}
