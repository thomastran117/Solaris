// @vitest-environment node
import { describe, expect, it } from "vitest";
import { NextRequest } from "next/server";
import {
  getRedirectUrl,
  unstable_doesMiddlewareMatch,
} from "next/experimental/testing/server";
import { config, proxy } from "./proxy";
import { PROTECTED_PREFIXES } from "./lib/routes";

const BASE = "http://localhost:3000";

function request(path: string, cookie?: string) {
  return new NextRequest(`${BASE}${path}`, {
    headers: cookie ? { cookie } : undefined,
  });
}

describe("proxy matcher", () => {
  it("runs for every protected prefix (kept in sync with PROTECTED_PREFIXES)", () => {
    for (const prefix of PROTECTED_PREFIXES) {
      expect(unstable_doesMiddlewareMatch({ config, url: `${prefix}` })).toBe(
        true
      );
      expect(unstable_doesMiddlewareMatch({ config, url: `${prefix}/x` })).toBe(
        true
      );
    }
  });

  it.each([
    "/",
    "/browse",
    "/products/1",
    "/api/auth/refresh",
    "/_next/static/a.js",
    "/login",
  ])("does not run for %s", (url) => {
    expect(unstable_doesMiddlewareMatch({ config, url })).toBe(false);
  });
});

describe("proxy", () => {
  it("redirects an anonymous visitor to login and keeps the path and query", () => {
    const res = proxy(request("/orders/abc?x=1"));
    expect(res.status).toBe(307);
    expect(getRedirectUrl(res)).toBe(
      `${BASE}/login?next=%2Forders%2Fabc%3Fx%3D1`
    );
  });

  it("lets a request with the refresh cookie through without inspecting it", () => {
    const res = proxy(request("/admin/products", "refreshToken=opaque"));
    expect(getRedirectUrl(res)).toBeNull();
    expect(res.headers.get("x-middleware-next")).toBe("1");
  });

  it("ignores unrelated cookies", () => {
    const res = proxy(request("/account", "theme=dark; refreshTokenX=1"));
    expect(getRedirectUrl(res)).toBe(`${BASE}/login?next=%2Faccount`);
  });

  it("lets shared public lists through although /lists is matched", () => {
    const res = proxy(request("/lists/public/sam-wish"));
    expect(getRedirectUrl(res)).toBeNull();
  });
});
