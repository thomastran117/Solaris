import type { Metadata } from "next";
import { Suspense } from "react";
import AdminPurchaseOrdersPage from "@views/admin/AdminPurchaseOrdersPage";

export const metadata: Metadata = { title: "Purchase orders" };

export default function Page() {
  return (
    // The view reads useSearchParams(), which needs a Suspense boundary.
    <Suspense>
      <AdminPurchaseOrdersPage />
    </Suspense>
  );
}
