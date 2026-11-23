import type { ApiEnvelope } from "../types/api";

/** True when `value` has the backend's common `ApiResponse` envelope shape. */
export function isEnvelope(value: unknown): value is ApiEnvelope<unknown> {
  return (
    typeof value === "object" &&
    value !== null &&
    "success" in value &&
    "data" in value &&
    "error" in value
  );
}

/** Returns the envelope payload, or the value itself when it is not enveloped. */
export function envelopeData<T>(value: unknown): T {
  return (isEnvelope(value) ? value.data : value) as T;
}
