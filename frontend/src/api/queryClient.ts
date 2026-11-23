import { QueryClient, isServer } from "@tanstack/react-query";

export function makeQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { staleTime: 30_000, retry: 1 },
    },
  });
}

let browserQueryClient: QueryClient | undefined;

/**
 * Returns a fresh client for every server render, so cached data never leaks
 * between requests, and one long-lived client in the browser.
 */
export function getQueryClient(): QueryClient {
  if (isServer) return makeQueryClient();
  browserQueryClient ??= makeQueryClient();
  return browserQueryClient;
}
