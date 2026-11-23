import { afterEach, describe, expect, it } from "vitest";
import { renderHook } from "@testing-library/react";
import { useAnims, useEntranceInitial } from "./useAnims";
import { markHydrated, resetHydratedForTests } from "./useHydrated";

afterEach(() => resetHydratedForTests());

describe("useAnims (SSR-safe entrance variants)", () => {
  it("renders hidden content as visible before hydration so server HTML is readable without JS", () => {
    const { result } = renderHook(() => useAnims());
    expect(result.current.fadeInUp.hidden).toEqual({ opacity: 1, y: 0 });
    expect(result.current.fadeIn.hidden).toEqual({ opacity: 1, y: 0 });
  });

  it("animates content mounted after hydration", () => {
    markHydrated();
    const { result } = renderHook(() => useAnims());
    expect(result.current.fadeInUp.hidden).toMatchObject({ opacity: 0 });
  });

  it("useEntranceInitial starts in the final state until hydration", () => {
    const initial = { opacity: 0, y: 16 };
    expect(renderHook(() => useEntranceInitial(initial)).result.current).toBe(
      false
    );
    markHydrated();
    expect(renderHook(() => useEntranceInitial(initial)).result.current).toBe(
      initial
    );
  });
});
