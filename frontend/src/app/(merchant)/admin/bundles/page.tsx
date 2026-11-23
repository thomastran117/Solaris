import type { Metadata } from "next";
import AdminBundlesPage from "@views/admin/AdminBundlesPage";

export const metadata: Metadata = { title: "Bundles" };

export default function Page() {
  return <AdminBundlesPage />;
}
