"use client";

import { usePathname, useRouter } from "next/navigation";
import { useCallback } from "react";

type Init = URLSearchParams | Record<string, string> | string;

/**
 * Replacement for React Router's `setSearchParams`: navigates to the current path
 * with new query parameters. Like React Router (and unlike Next.js's default), it
 * keeps the scroll position, so filter changes do not jump to the top of the page.
 */
export function useSetSearchParams() {
  const router = useRouter();
  const pathname = usePathname() ?? "/";
  return useCallback(
    (next: Init, options: { replace?: boolean } = {}) => {
      const qs = new URLSearchParams(next).toString();
      const href = qs ? `${pathname}?${qs}` : pathname;
      if (options.replace) router.replace(href, { scroll: false });
      else router.push(href, { scroll: false });
    },
    [router, pathname]
  );
}
