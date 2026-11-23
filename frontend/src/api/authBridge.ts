import type { AppStore } from "../stores";

/**
 * Connects non-React modules (the axios client, session refresh) to the browser's
 * Redux store without importing a module-level store singleton, which would be
 * shared between requests when Next.js renders on the server.
 *
 * `StoreProvider` binds the store during its first browser render. Nothing binds on
 * the server, so server-side code can never read a user's access token from here.
 */
let boundStore: AppStore | null = null;

export function bindApiStore(store: AppStore): void {
  boundStore = store;
}

export function getBoundStore(): AppStore | null {
  return boundStore;
}

export function getAccessToken(): string | null {
  return boundStore?.getState().auth.accessToken ?? null;
}
