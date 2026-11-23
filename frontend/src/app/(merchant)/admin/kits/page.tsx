import type { Metadata } from "next";
import AdminKitsPage from "@views/admin/AdminKitsPage";

export const metadata: Metadata = { title: "Kits" };

export default function Page() {
  return <AdminKitsPage />;
}
