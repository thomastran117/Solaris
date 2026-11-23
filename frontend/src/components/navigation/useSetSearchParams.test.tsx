import { describe, expect, it } from "vitest";
import { renderHook } from "@testing-library/react";
import { useSetSearchParams } from "./useSetSearchParams";
import { mockRouter, setMockUrl } from "../../test/nextNavigation";

describe("useSetSearchParams", () => {
  it("replaces the query on the current path without scrolling", () => {
    setMockUrl("/browse?q=lamp");
    const { result } = renderHook(() => useSetSearchParams());
    result.current(new URLSearchParams({ q: "desk", page: "0" }), {
      replace: true,
    });
    expect(mockRouter.replace).toHaveBeenCalledWith("/browse?q=desk&page=0", {
      scroll: false,
    });
    expect(mockRouter.push).not.toHaveBeenCalled();
  });

  it("pushes by default and accepts a plain object", () => {
    setMockUrl("/c/abc/compare");
    const { result } = renderHook(() => useSetSearchParams());
    result.current({ type: "bundle" });
    expect(mockRouter.push).toHaveBeenCalledWith("/c/abc/compare?type=bundle", {
      scroll: false,
    });
  });

  it("drops the question mark when no parameters remain", () => {
    setMockUrl("/browse?q=lamp");
    const { result } = renderHook(() => useSetSearchParams());
    result.current(new URLSearchParams());
    expect(mockRouter.push).toHaveBeenCalledWith("/browse", { scroll: false });
  });
});
