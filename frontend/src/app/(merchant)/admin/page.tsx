import type { Metadata } from "next";
import AdminOverviewPage from "@views/admin/AdminOverviewPage";

export const metadata: Metadata = { title: "Seller dashboard" };

export default function Page() {
  return <AdminOverviewPage />;
}
