import { describe, expect, it } from "vitest";
import { safeNextPath } from "./safeRedirect";

describe("safeNextPath", () => {
  it.each([
    ["/orders/42", "/orders/42"],
    ["/account?upgrade=success", "/account?upgrade=success"],
    ["/c/abc?q=lamp&page=2#reviews", "/c/abc?q=lamp&page=2#reviews"],
    ["/admin/orders/1", "/admin/orders/1"],
    ["/invite/accept/tok-123", "/invite/accept/tok-123"],
    ["/a/../orders", "/orders"],
    ["/loginhelp", "/loginhelp"],
  ])("accepts same-origin path %s", (raw, expected) => {
    expect(safeNextPath(raw)).toBe(expected);
  });

  it.each([
    // missing / not a path
    [null],
    [undefined],
    [""],
    ["orders"],
    // absolute and scheme URLs
    ["https://evil.test/orders"],
    ["http:evil.test"],
    ["javascript:alert(1)"],
    ["data:text/html,hi"],
    // protocol-relative and backslash variants browsers treat as another host
    ["//evil.test"],
    ["///evil.test"],
    ["/\\evil.test"],
    ["\\\\evil.test"],
    ["/\\/evil.test"],
    // whitespace / control characters browsers strip before parsing
    ["/\t/evil.test"],
    [" /orders"],
    ["/orders\n"],
    ["/\u0000/evil.test"],
    // redirect loops
    ["/login"],
    ["/login?next=/orders"],
    ["/login/"],
    ["/auth/google"],
    // absurd length
    ["/" + "a".repeat(3000)],
  ])("rejects %j", (raw) => {
    expect(safeNextPath(raw as string | null | undefined)).toBeNull();
  });
});
