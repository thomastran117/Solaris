import type { Metadata } from "next";
import AdminReportsPage from "@views/admin/AdminReportsPage";

export const metadata: Metadata = { title: "Reports" };

export default function Page() {
  return <AdminReportsPage />;
}
