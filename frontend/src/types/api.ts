export interface ApiError {
  code: string;
  details?: Record<string, unknown> | null;
}

export interface ApiMeta {
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
  hasNext?: boolean;
  hasPrevious?: boolean;
  nextCursor?: string;
  hasMore?: boolean;
}

export interface ApiEnvelope<T> {
  success: boolean;
  message: string;
  data: T | null;
  error: ApiError | null;
  meta: ApiMeta | null;
}
