import type { Metadata } from "next";
import AdminBulkImportPage from "@views/admin/AdminBulkImportPage";

export const metadata: Metadata = { title: "Bulk import" };

export default function Page() {
  return <AdminBulkImportPage />;
}
