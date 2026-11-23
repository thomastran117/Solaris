import type { Metadata } from "next";
import AdminDisputesPage from "@views/admin/AdminDisputesPage";

export const metadata: Metadata = { title: "Chargebacks" };

export default function Page() {
  return <AdminDisputesPage />;
}
