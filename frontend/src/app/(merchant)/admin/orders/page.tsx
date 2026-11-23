import type { Metadata } from "next";
import CompanyOrdersPage from "@views/admin/CompanyOrdersPage";

export const metadata: Metadata = { title: "Fulfillment" };

export default function Page() {
  return <CompanyOrdersPage />;
}
