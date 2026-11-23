import type { Metadata } from "next";
import AdminWebhooksPage from "@views/admin/AdminWebhooksPage";

export const metadata: Metadata = { title: "Webhooks" };

export default function Page() {
  return <AdminWebhooksPage />;
}
