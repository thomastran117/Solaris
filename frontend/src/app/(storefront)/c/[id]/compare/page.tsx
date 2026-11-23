import type { Metadata } from "next";
import { Suspense } from "react";
import ComparisonPage from "@views/ComparisonPage";

export const metadata: Metadata = {
  title: "Compare",
  robots: { index: false },
};

export default function Page() {
  return (
    // The view reads useSearchParams(), which needs a Suspense boundary.
    <Suspense>
      <ComparisonPage />
    </Suspense>
  );
}
