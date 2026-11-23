import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { CreateOrderRequest } from "../types/order";

const { create } = vi.hoisted(() => ({ create: vi.fn() }));
vi.mock("../api/orders", () => ({ ordersApi: { create } }));

const { useCheckoutSubmit, checkoutFingerprint } =
  await import("./useCheckoutSubmit");

const request: CreateOrderRequest = {
  items: [{ productId: "p1", variantId: null, quantity: 1 }],
  currency: "USD",
  fulfillmentMethod: "DELIVERY",
  shipRecipientName: "Sam",
};

const ORDER = { id: "o1", status: "RESERVED" };

function httpError(status: number) {
  return Object.assign(new Error(`HTTP ${status}`), {
    isAxiosError: true,
    response: { status, data: {} },
    apiMessage: `status ${status}`,
  });
}
const networkError = () =>
  Object.assign(new Error("Network Error"), { isAxiosError: true });

const keysUsed = () => create.mock.calls.map((call) => call[1] as string);

function setup() {
  return renderHook(() => useCheckoutSubmit({ retryDelaysMs: [0, 0] }));
}

beforeEach(() => {
  create.mockReset();
  window.sessionStorage.clear();
});

describe("useCheckoutSubmit", () => {
  it("creates the order with an idempotency key and then forgets the key", async () => {
    create.mockResolvedValue({ data: ORDER });
    const { result } = setup();
    let outcome;
    await act(async () => {
      outcome = await result.current.submit(request);
    });
    expect(outcome).toEqual({ kind: "created", order: ORDER });
    expect(keysUsed()[0]).toMatch(/^[0-9a-f-]{36}$/);
    expect(window.sessionStorage.length).toBe(0);
  });

  it("retries a lost response with the same key", async () => {
    create
      .mockRejectedValueOnce(networkError())
      .mockResolvedValueOnce({ data: ORDER });
    const { result } = setup();
    let outcome;
    await act(async () => {
      outcome = await result.current.submit(request);
    });
    expect(outcome).toMatchObject({ kind: "created" });
    const [first, second] = keysUsed();
    expect(second).toBe(first);
  });

  it("keeps the key and waits while a lost request is still in flight (409)", async () => {
    create
      .mockRejectedValueOnce(networkError())
      .mockRejectedValueOnce(httpError(409))
      .mockResolvedValueOnce({ data: ORDER });
    const { result } = setup();
    await act(async () => {
      await result.current.submit(request);
    });
    expect(new Set(keysUsed()).size).toBe(1);
    expect(create).toHaveBeenCalledTimes(3);
  });

  it("keeps the key after retries run out so a later retry can't duplicate", async () => {
    create.mockRejectedValue(networkError());
    const { result } = setup();
    let outcome;
    await act(async () => {
      outcome = await result.current.submit(request);
    });
    expect(outcome).toMatchObject({ kind: "error" });
    const first = keysUsed()[0];

    create.mockReset().mockResolvedValue({ data: ORDER });
    // A fresh hook instance stands in for a page reload.
    const reloaded = setup();
    await act(async () => {
      await reloaded.result.current.submit(request);
    });
    expect(keysUsed()[0]).toBe(first);
  });

  it("treats a 409 on a fresh key as a business conflict and rotates", async () => {
    create.mockRejectedValueOnce(httpError(409));
    const { result } = setup();
    let outcome;
    await act(async () => {
      outcome = await result.current.submit(request);
    });
    expect(outcome).toEqual({ kind: "error", message: "status 409" });
    expect(create).toHaveBeenCalledTimes(1);

    create.mockResolvedValueOnce({ data: ORDER });
    await act(async () => {
      await result.current.submit(request);
    });
    const [first, second] = keysUsed();
    expect(second).not.toBe(first);
  });

  it("reports step-up (428) and rotates the key for the confirmed retry", async () => {
    create.mockRejectedValueOnce(httpError(428));
    const { result } = setup();
    let outcome;
    await act(async () => {
      outcome = await result.current.submit(request);
    });
    expect(outcome).toEqual({ kind: "stepUp" });

    create.mockResolvedValueOnce({ data: ORDER });
    await act(async () => {
      await result.current.submit({ ...request, riskVerificationToken: "tok" });
    });
    const [first, second] = keysUsed();
    expect(second).not.toBe(first);
    expect(create.mock.calls[1][0]).toMatchObject({
      riskVerificationToken: "tok",
    });
  });

  it("ignores a second submit while one is in flight", async () => {
    let resolve!: (value: unknown) => void;
    create.mockImplementation(() => new Promise((r) => (resolve = r)));
    const { result } = setup();
    let first: Promise<unknown>;
    let second: unknown;
    await act(async () => {
      first = result.current.submit(request);
      second = await result.current.submit(request);
    });
    expect(second).toBeNull();
    await act(async () => {
      resolve({ data: ORDER });
      await first;
    });
    expect(create).toHaveBeenCalledTimes(1);
  });

  it("ignores the step-up token when fingerprinting the cart", () => {
    expect(checkoutFingerprint(request)).toBe(
      checkoutFingerprint({ ...request, riskVerificationToken: "abc" })
    );
    expect(checkoutFingerprint(request)).not.toBe(
      checkoutFingerprint({ ...request, currency: "CAD" })
    );
  });
});
