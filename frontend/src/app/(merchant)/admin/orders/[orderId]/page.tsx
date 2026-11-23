import type { Metadata } from "next";
import CompanyOrderDetailPage from "@views/admin/CompanyOrderDetailPage";

export const metadata: Metadata = { title: "Fulfillment order" };

export default function Page() {
  return <CompanyOrderDetailPage />;
}
