"use client";

import { useCallback, useRef, useState } from "react";
import { ordersApi } from "../api/orders";
import type { CreateOrderRequest, Order } from "../types/order";
import {
  apiErrorMessage,
  apiErrorStatus,
  isNetworkError,
} from "../lib/apiError";

export type SubmitOutcome =
  | { kind: "created"; order: Order }
  /** HTTP 428: the risk engine emailed a confirmation link (step-up). */
  | { kind: "stepUp" }
  | { kind: "error"; message: string };

const KEY_PREFIX = "shopwave.checkoutKey:";
const DEFAULT_RETRY_DELAYS_MS = [1_000, 3_000];

/** Small, stable string hash (djb2) for keying the idempotency key per cart. */
export function checkoutFingerprint(request: CreateOrderRequest): string {
  // The step-up token changes between attempts but not the order itself.
  const { riskVerificationToken: _ignored, ...order } = request;
  const text = JSON.stringify(order);
  let hash = 5381;
  for (let i = 0; i < text.length; i++) {
    hash = ((hash << 5) + hash + text.charCodeAt(i)) | 0;
  }
  return (hash >>> 0).toString(36);
}

function readKey(storageKey: string): string | null {
  try {
    return window.sessionStorage.getItem(storageKey);
  } catch {
    return null;
  }
}

function writeKey(storageKey: string, key: string | null): void {
  try {
    if (key) window.sessionStorage.setItem(storageKey, key);
    else window.sessionStorage.removeItem(storageKey);
  } catch {
    // Without storage the key still lives in memory for this page view.
  }
}

const sleep = (ms: number) =>
  new Promise<void>((resolve) => setTimeout(resolve, ms));

/**
 * Places an order with an `Idempotency-Key` that follows the backend contract
 * (OrderController): a claimed key stays claimed for ten minutes and is never
 * released on failure, and a key whose order was stored returns that order.
 *
 * - The key is reused only when the outcome is unknown (no response, or 409
 *   "in flight" after a lost response), so a retry can never create a second
 *   order.
 * - After any definite answer (success, 4xx, 5xx, 428) the key is rotated, so
 *   the next attempt isn't rejected as a duplicate.
 * - It is mirrored to sessionStorage per cart, so reloading mid-request and
 *   resubmitting the same cart reuses it.
 * - Concurrent submits are ignored.
 */
export function useCheckoutSubmit({
  retryDelaysMs = DEFAULT_RETRY_DELAYS_MS,
}: { retryDelaysMs?: number[] } = {}) {
  const inFlight = useRef(false);
  const [pending, setPending] = useState(false);

  const submit = useCallback(
    async (request: CreateOrderRequest): Promise<SubmitOutcome | null> => {
      if (inFlight.current) return null;
      inFlight.current = true;
      setPending(true);

      const storageKey = KEY_PREFIX + checkoutFingerprint(request);
      const key = readKey(storageKey) ?? crypto.randomUUID();
      writeKey(storageKey, key);
      const rotate = () => writeKey(storageKey, null);
      let outcomeUnknown = false;

      try {
        for (let attempt = 0; ; attempt++) {
          try {
            const res = await ordersApi.create(request, key);
            rotate();
            return { kind: "created", order: res.data };
          } catch (error) {
            const status = apiErrorStatus(error);
            const canRetry = attempt < retryDelaysMs.length;

            if (isNetworkError(error)) {
              outcomeUnknown = true;
              if (canRetry) {
                await sleep(retryDelaysMs[attempt]);
                continue;
              }
              return {
                kind: "error",
                message:
                  "We couldn't confirm your order because the connection dropped. Try again: you won't be charged twice.",
              };
            }

            if (status === 409 && outcomeUnknown) {
              // Our earlier attempt reached the server and is still processing.
              if (canRetry) {
                await sleep(retryDelaysMs[attempt]);
                continue;
              }
              return {
                kind: "error",
                message:
                  "Your order is still being processed. Check your orders in a minute before trying again.",
              };
            }

            rotate();
            if (status === 428) return { kind: "stepUp" };
            return {
              kind: "error",
              message: apiErrorMessage(
                error,
                "We couldn't place your order. Please try again."
              ),
            };
          }
        }
      } finally {
        inFlight.current = false;
        setPending(false);
      }
    },
    [retryDelaysMs]
  );

  return { submit, pending };
}
