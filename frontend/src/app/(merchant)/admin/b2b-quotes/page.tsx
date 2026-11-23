import type { Metadata } from "next";
import AdminB2BQuotesPage from "@views/admin/AdminB2BQuotesPage";

export const metadata: Metadata = { title: "B2B quotes" };

export default function Page() {
  return <AdminB2BQuotesPage />;
}
