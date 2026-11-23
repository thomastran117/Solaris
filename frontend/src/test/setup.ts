import "@testing-library/jest-dom";
import { afterEach, vi } from "vitest";
import { resetNavigationMock } from "./nextNavigation";

// App Router hooks and links only work inside a mounted Next.js app; tests use the
// controllable doubles in ./nextNavigation and ./nextLink instead.
vi.mock("next/navigation", () => import("./nextNavigation"));
vi.mock("next/link", () => import("./nextLink"));

afterEach(() => {
  resetNavigationMock();
});

class MockIntersectionObserver implements IntersectionObserver {
  readonly root: Element | Document | null = null;
  readonly rootMargin: string = "";
  readonly thresholds: ReadonlyArray<number> = [];

  disconnect(): void {}
  observe(): void {}
  takeRecords(): IntersectionObserverEntry[] {
    return [];
  }
  unobserve(): void {}
}

globalThis.IntersectionObserver =
  MockIntersectionObserver as unknown as typeof IntersectionObserver;

// Radix primitives (Dialog, DropdownMenu, Tabs) and the theme provider use
// browser APIs jsdom doesn't implement. Skipped for node-environment suites.
if (typeof window !== "undefined") installBrowserPolyfills();

function installBrowserPolyfills(): void {
  if (typeof window.matchMedia !== "function") {
    window.matchMedia = (query: string): MediaQueryList => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    });
  }

  class MockResizeObserver implements ResizeObserver {
    disconnect(): void {}
    observe(): void {}
    unobserve(): void {}
  }
  globalThis.ResizeObserver ??=
    MockResizeObserver as unknown as typeof ResizeObserver;

  Element.prototype.hasPointerCapture ??= () => false;
  Element.prototype.releasePointerCapture ??= () => {};
  Element.prototype.scrollIntoView ??= () => {};
}
