/**
 * Test double for `next/navigation`, registered globally in `src/test/setup.ts`.
 *
 * The mock keeps a URL in a tiny external store, so components that read
 * `usePathname()` / `useSearchParams()` re-render when code under test calls
 * `router.push()` or `router.replace()`, much like React Router's MemoryRouter did.
 *
 *   setMockUrl("/browse?q=lamp");                    // before render
 *   setMockUrl("/c/abc/compare", { params: { id: "abc" } });
 *   expect(mockRouter.push).toHaveBeenCalledWith("/products/1");
 */
import { useSyncExternalStore } from "react";
import { vi } from "vitest";

type Params = Record<string, string | string[]>;

interface NavState {
  url: URL;
  params: Params;
}

let state: NavState = { url: new URL("http://localhost/"), params: {} };
const listeners = new Set<() => void>();

function emit() {
  for (const l of listeners) l();
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

function navigate(href: string) {
  state = { ...state, url: new URL(href, state.url) };
  emit();
}

export function setMockUrl(
  href: string,
  options: { params?: Params } = {}
): void {
  state = {
    url: new URL(href, "http://localhost"),
    params: options.params ?? state.params,
  };
  emit();
}

export function setMockParams(params: Params): void {
  state = { ...state, params };
  emit();
}

export const mockRouter = {
  push: vi.fn((href: string) => navigate(href)),
  replace: vi.fn((href: string) => navigate(href)),
  back: vi.fn(),
  forward: vi.fn(),
  refresh: vi.fn(),
  prefetch: vi.fn(),
};

export function resetNavigationMock(): void {
  state = { url: new URL("http://localhost/"), params: {} };
  for (const fn of Object.values(mockRouter)) fn.mockClear();
}

const getUrl = () => state.url;
const getParams = () => state.params;

export function useRouter() {
  return mockRouter;
}

export function usePathname(): string {
  return useSyncExternalStore(subscribe, getUrl, getUrl).pathname;
}

export function useSearchParams(): URLSearchParams {
  const url = useSyncExternalStore(subscribe, getUrl, getUrl);
  // Same URL object until the next navigation, so memoising on it is stable.
  return searchParamsFor(url);
}

const searchParamsCache = new WeakMap<URL, URLSearchParams>();
function searchParamsFor(url: URL): URLSearchParams {
  let sp = searchParamsCache.get(url);
  if (!sp) {
    sp = new URLSearchParams(url.search);
    searchParamsCache.set(url, sp);
  }
  return sp;
}

export function useParams<T extends Params = Params>(): T {
  return useSyncExternalStore(subscribe, getParams, getParams) as T;
}

export function redirect(href: string): never {
  throw new Error(`NEXT_REDIRECT:${href}`);
}

export function notFound(): never {
  throw new Error("NEXT_NOT_FOUND");
}
