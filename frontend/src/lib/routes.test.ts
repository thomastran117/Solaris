import { describe, expect, it } from "vitest";
import { isProtectedPath, loginUrlFor } from "./routes";

describe("isProtectedPath", () => {
  it.each([
    "/account",
    "/admin/products",
    "/admin/orders/1",
    "/b2b/quotes",
    "/dashboard",
    "/feed",
    "/feedback",
    "/following",
    "/lists",
    "/lists/abc",
    "/loyalty",
    "/orders",
    "/orders/42",
    "/price-watches",
  ])("protects %s", (path) => {
    expect(isProtectedPath(path)).toBe(true);
  });

  it.each([
    "/",
    "/browse",
    "/products/1",
    "/c/abc",
    "/c/abc/compare",
    "/collections/winter",
    "/kits/c/k",
    "/lists/public/sam-wish",
    "/gift-cards",
    "/login",
    "/auth/google",
    "/invite/accept/tok",
    "/accountant", // prefix match must respect segment boundaries
    "/feeds",
    "/terms-and-conditions",
  ])("leaves %s public", (path) => {
    expect(isProtectedPath(path)).toBe(false);
  });
});

describe("loginUrlFor", () => {
  it("encodes the full return path", () => {
    expect(loginUrlFor("/orders/x?y=1")).toBe(
      "/login?next=%2Forders%2Fx%3Fy%3D1"
    );
  });
});
