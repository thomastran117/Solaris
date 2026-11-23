import type { Metadata } from "next";
import { Suspense } from "react";
import AccountPage from "@views/AccountPage";

export const metadata: Metadata = { title: "Account" };

export default function Page() {
  return (
    // The view reads useSearchParams(), which needs a Suspense boundary.
    <Suspense>
      <AccountPage />
    </Suspense>
  );
}
