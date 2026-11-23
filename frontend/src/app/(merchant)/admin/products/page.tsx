import type { Metadata } from "next";
import AdminProductsPage from "@views/admin/AdminProductsPage";

export const metadata: Metadata = { title: "Products" };

export default function Page() {
  return <AdminProductsPage />;
}
