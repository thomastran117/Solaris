import type { Metadata } from "next";
import AdminCollectionProductsPage from "@views/admin/AdminCollectionProductsPage";

export const metadata: Metadata = { title: "Collection products" };

export default function Page() {
  return <AdminCollectionProductsPage />;
}
