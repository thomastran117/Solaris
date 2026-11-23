import type { Metadata } from "next";
import AdminInventoryTransfersPage from "@views/admin/AdminInventoryTransfersPage";

export const metadata: Metadata = { title: "Inventory transfers" };

export default function Page() {
  return <AdminInventoryTransfersPage />;
}
