import type { CheckoutValues } from "../schemas/checkout";

/**
 * The checkout form saved when the risk engine asks for email confirmation, so
 * the link in that email (`/checkout?stepUpToken=…`) can resubmit the same
 * order. Kept briefly and on this device only.
 */
export interface CheckoutDraft {
  currency: string;
  values: CheckoutValues;
  savedAt: number;
}

const KEY = "shopwave.checkoutDraft";
export const CHECKOUT_DRAFT_TTL_MS = 30 * 60 * 1000;

export function saveCheckoutDraft(draft: Omit<CheckoutDraft, "savedAt">): void {
  try {
    window.localStorage.setItem(
      KEY,
      JSON.stringify({ ...draft, savedAt: Date.now() })
    );
  } catch {
    // Without storage the buyer re-enters details after confirming.
  }
}

export function loadCheckoutDraft(now = Date.now()): CheckoutDraft | null {
  try {
    const raw = window.localStorage.getItem(KEY);
    if (!raw) return null;
    const draft = JSON.parse(raw) as CheckoutDraft;
    if (
      typeof draft?.savedAt !== "number" ||
      now - draft.savedAt > CHECKOUT_DRAFT_TTL_MS ||
      typeof draft.currency !== "string" ||
      typeof draft.values !== "object"
    ) {
      clearCheckoutDraft();
      return null;
    }
    return draft;
  } catch {
    return null;
  }
}

export function clearCheckoutDraft(): void {
  try {
    window.localStorage.removeItem(KEY);
  } catch {
    // ignore
  }
}

/** Accepts a pasted confirmation link or a bare token. */
export function extractStepUpToken(input: string): string | null {
  const text = input.trim();
  if (!text) return null;
  try {
    const url = new URL(text);
    return url.searchParams.get("stepUpToken");
  } catch {
    return /^[\w-]{8,64}$/.test(text) ? text : null;
  }
}
