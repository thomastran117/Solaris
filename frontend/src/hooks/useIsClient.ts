import { useSyncExternalStore } from "react";

const subscribe = () => () => {};

/**
 * `false` during server rendering and hydration, `true` afterwards. Use it to gate
 * browser-only output (portals, `window` reads) without a hydration mismatch.
 */
export function useIsClient(): boolean {
  return useSyncExternalStore(
    subscribe,
    () => true,
    () => false
  );
}
