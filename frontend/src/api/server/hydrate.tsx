import "server-only";
import {
  dehydrate,
  HydrationBoundary,
  type QueryKey,
} from "@tanstack/react-query";
import { makeQueryClient } from "../queryClient";

export type Prefetched = ReadonlyArray<readonly [QueryKey, unknown]>;

/**
 * Seeds server-fetched data into the client's TanStack Query cache under the given
 * keys. Entries whose data is `null` (fetch failed or skipped) are left out, so the
 * view fetches them in the browser as before.
 */
export function Hydrate({
  entries,
  children,
}: {
  entries: Prefetched;
  children: React.ReactNode;
}) {
  const queryClient = makeQueryClient();
  for (const [key, data] of entries) {
    if (data !== null && data !== undefined)
      queryClient.setQueryData(key, data);
  }
  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      {children}
    </HydrationBoundary>
  );
}
