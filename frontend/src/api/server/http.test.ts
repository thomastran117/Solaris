import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiHttpError, serverGet } from "./http";
import { loadOptional, loadPrimary } from "./storefront";

const fetchMock = vi.fn();

function respond(status: number, body: unknown) {
  return Promise.resolve(
    new Response(JSON.stringify(body), {
      status,
      headers: { "Content-Type": "application/json" },
    })
  );
}

beforeEach(() => {
  vi.stubGlobal("fetch", fetchMock);
  vi.stubEnv("API_INTERNAL_URL", "http://backend:8090/");
});
afterEach(() => {
  fetchMock.mockReset();
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

describe("serverGet", () => {
  it("calls the internal API, unwraps the envelope, and skips empty query values", async () => {
    fetchMock.mockReturnValue(
      respond(200, {
        success: true,
        message: "ok",
        data: { id: "p1" },
        error: null,
        meta: null,
      })
    );
    const data = await serverGet<{ id: string }>(
      "/marketplaces/m/catalog/products",
      {
        searchParams: {
          q: "lamp",
          brand: undefined,
          category: null,
          empty: "",
          page: 0,
        },
      }
    );
    expect(data).toEqual({ id: "p1" });
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe(
      "http://backend:8090/api/marketplaces/m/catalog/products?q=lamp&page=0"
    );
    expect(init.next).toEqual({ revalidate: 60, tags: undefined });
  });

  it("never sends credentials: server HTML and the data cache must stay public", async () => {
    fetchMock.mockReturnValue(
      respond(200, {
        success: true,
        message: "",
        data: [],
        error: null,
        meta: null,
      })
    );
    await serverGet("/companies/c1/public");
    const [, init] = fetchMock.mock.calls[0];
    const headers = new Headers(init.headers);
    expect(headers.get("authorization")).toBeNull();
    expect(headers.get("cookie")).toBeNull();
    expect(init.credentials).toBeUndefined();
    expect([...headers.keys()]).toEqual(["accept"]);
  });

  it("throws ApiHttpError with the status and envelope error code", async () => {
    fetchMock.mockReturnValue(
      respond(404, {
        success: false,
        message: "x",
        data: null,
        error: { code: "NOT_FOUND" },
        meta: null,
      })
    );
    await expect(serverGet("/x")).rejects.toMatchObject({
      status: 404,
      code: "NOT_FOUND",
    });
  });
});

describe("loadPrimary / loadOptional", () => {
  const silence = () => vi.spyOn(console, "error").mockImplementation(() => {});

  it("renders the not-found page for a missing or malformed entity", async () => {
    await expect(
      loadPrimary(() => Promise.reject(new ApiHttpError(404, null, "")))
    ).rejects.toThrow("NEXT_NOT_FOUND");
    await expect(
      loadPrimary(() => Promise.reject(new ApiHttpError(400, null, "")))
    ).rejects.toThrow("NEXT_NOT_FOUND");
  });

  it("degrades to no prefetch when the backend fails, so the browser retries", async () => {
    const spy = silence();
    await expect(
      loadPrimary(() => Promise.reject(new ApiHttpError(503, null, "")))
    ).resolves.toBeNull();
    await expect(
      loadPrimary(() => Promise.reject(new TypeError("fetch failed")))
    ).resolves.toBeNull();
    await expect(
      loadOptional(() => Promise.reject(new ApiHttpError(404, null, "")))
    ).resolves.toBeNull();
    expect(spy).toHaveBeenCalled();
    spy.mockRestore();
  });

  it("returns the data on success", async () => {
    await expect(
      loadPrimary(() => Promise.resolve({ ok: 1 }))
    ).resolves.toEqual({ ok: 1 });
  });
});
