import type { Metadata } from "next";
import { Suspense } from "react";
import BrowsePage from "@views/BrowsePage";

export const metadata: Metadata = { title: "Browse" };

export default function Page() {
  return (
    // The view reads useSearchParams(), which needs a Suspense boundary.
    <Suspense>
      <BrowsePage />
    </Suspense>
  );
}
