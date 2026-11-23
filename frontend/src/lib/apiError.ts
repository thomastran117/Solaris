import type { ApiError } from "../types/api";

/**
 * Helpers for errors thrown by the axios client in `src/api.ts`, which attaches
 * the backend envelope's `apiError` and `apiMessage` to rejected requests.
 */
interface ClientError {
  apiError?: ApiError | null;
  apiMessage?: string;
  response?: {
    status?: number;
    data?: { message?: string; error?: ApiError | null };
  };
  code?: string;
  isAxiosError?: boolean;
}

function asClientError(error: unknown): ClientError | null {
  return typeof error === "object" && error !== null
    ? (error as ClientError)
    : null;
}

/** HTTP status of a failed request, or `null` when no response arrived. */
export function apiErrorStatus(error: unknown): number | null {
  return asClientError(error)?.response?.status ?? null;
}

/** The backend's stable error code (e.g. `RATE_LIMITED`), when present. */
export function apiErrorCode(error: unknown): string | null {
  const e = asClientError(error);
  return e?.apiError?.code ?? e?.response?.data?.error?.code ?? null;
}

/** Envelope `error.details`, for structured failures such as validation. */
export function apiErrorDetails(
  error: unknown
): Record<string, unknown> | null {
  const e = asClientError(error);
  return e?.apiError?.details ?? e?.response?.data?.error?.details ?? null;
}

/**
 * True when the request never got a response (offline, DNS, timeout), so its
 * outcome on the server is unknown.
 */
export function isNetworkError(error: unknown): boolean {
  const e = asClientError(error);
  return !!e?.isAxiosError && !e.response;
}

/** A message safe to show the user, preferring the backend's own wording. */
export function apiErrorMessage(error: unknown, fallback: string): string {
  const e = asClientError(error);
  if (!e) return fallback;
  if (isNetworkError(error)) {
    return "We couldn't reach the server. Check your connection and try again.";
  }
  if (e.response?.status === 429) {
    return "Too many attempts. Please wait a moment and try again.";
  }
  return e.apiMessage || e.response?.data?.message || fallback;
}
