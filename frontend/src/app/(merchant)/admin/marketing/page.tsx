import type { Metadata } from "next";
import AdminMarketingPage from "@views/admin/AdminMarketingPage";

export const metadata: Metadata = { title: "Marketing" };

export default function Page() {
  return <AdminMarketingPage />;
}
