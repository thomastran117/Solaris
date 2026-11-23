import type { Metadata } from "next";
import AdminLoyaltyPage from "@views/admin/AdminLoyaltyPage";

export const metadata: Metadata = { title: "Loyalty program" };

export default function Page() {
  return <AdminLoyaltyPage />;
}
