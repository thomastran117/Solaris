import { useSyncExternalStore } from "react";

/**
 * Tracks whether the initial server-rendered page has finished hydrating.
 *
 * During server rendering and hydration `useHydrated()` returns `false`, matching the
 * server snapshot, so markup is identical on both sides. After `markHydrated()` runs
 * (once, from the root providers' effect), components that mount later see `true`.
 * Use it to disable entrance animations for content that was already visible in the
 * server HTML while keeping them for content mounted after client navigation.
 */
let hydrated = false;

const subscribe = () => () => {};
const getSnapshot = () => hydrated;
const getServerSnapshot = () => false;

export function markHydrated(): void {
  hydrated = true;
}

export function useHydrated(): boolean {
  return useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);
}

/** Test hook: restore the pre-hydration state between tests. */
export function resetHydratedForTests(): void {
  hydrated = false;
}
