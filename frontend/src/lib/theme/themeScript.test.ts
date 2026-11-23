import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { buildThemeScript } from "./themeScript";

const KEY = "shopwave-theme";

function run(
  script: string,
  { path = "/", stored = null as string | null, systemDark = false } = {}
) {
  window.history.replaceState(null, "", path);
  if (stored === null) window.localStorage.removeItem(KEY);
  else window.localStorage.setItem(KEY, stored);
  vi.spyOn(window, "matchMedia").mockImplementation(
    (query: string) =>
      ({
        matches: systemDark && query.includes("dark"),
        media: query,
      }) as MediaQueryList
  );
  // The script is a self-contained IIFE string, exactly as served in <head>.
  new Function(script)();
  return document.documentElement;
}

describe("buildThemeScript", () => {
  const script = buildThemeScript({
    forceAll: false,
    forcedPrefixes: ["/admin", "/lists"],
    exceptions: ["/lists/public"],
    storageKey: KEY,
  });

  beforeEach(() => {
    document.documentElement.className = "";
    document.documentElement.style.colorScheme = "";
  });

  afterEach(() => {
    vi.restoreAllMocks();
    window.localStorage.clear();
  });

  it("follows the system preference when nothing is stored", () => {
    expect(run(script, { systemDark: true }).classList.contains("dark")).toBe(
      true
    );
    expect(run(script, { systemDark: false }).classList.contains("dark")).toBe(
      false
    );
  });

  it("lets a stored choice override the system", () => {
    const root = run(script, { stored: "light", systemDark: true });
    expect(root.classList.contains("dark")).toBe(false);
    expect(root.style.colorScheme).toBe("light");

    expect(run(script, { stored: "dark" }).classList.contains("dark")).toBe(
      true
    );
  });

  it("ignores unknown stored values", () => {
    expect(
      run(script, { stored: "sepia", systemDark: false }).classList.contains(
        "dark"
      )
    ).toBe(false);
  });

  it("forces dark on legacy prefixes and their children only", () => {
    expect(
      run(script, {
        path: "/admin/orders",
        stored: "light",
      }).classList.contains("dark")
    ).toBe(true);
    expect(
      run(script, { path: "/admin", stored: "light" }).classList.contains(
        "dark"
      )
    ).toBe(true);
    expect(
      run(script, {
        path: "/administrator",
        stored: "light",
      }).classList.contains("dark")
    ).toBe(false);
  });

  it("leaves migrated exceptions under a forced prefix alone", () => {
    expect(
      run(script, { path: "/lists/abc", stored: "light" }).classList.contains(
        "dark"
      )
    ).toBe(true);
    expect(
      run(script, {
        path: "/lists/public/sam-wish",
        stored: "light",
      }).classList.contains("dark")
    ).toBe(false);
  });

  it("forces dark everywhere when forceAll is set", () => {
    const all = buildThemeScript({
      forceAll: true,
      forcedPrefixes: [],
      storageKey: KEY,
    });
    expect(run(all, { stored: "light" }).classList.contains("dark")).toBe(true);
  });

  it("does not throw when storage is unavailable", () => {
    vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new Error("blocked");
    });
    expect(() => run(script, { systemDark: true })).not.toThrow();
    expect(document.documentElement.classList.contains("dark")).toBe(true);
  });
});
