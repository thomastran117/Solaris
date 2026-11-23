import type { Metadata } from "next";
import { Suspense } from "react";
import CompanyPage from "@views/CompanyPage";

export const metadata: Metadata = { title: "Store" };

export default function Page() {
  return (
    // The view reads useSearchParams(), which needs a Suspense boundary.
    <Suspense>
      <CompanyPage />
    </Suspense>
  );
}
