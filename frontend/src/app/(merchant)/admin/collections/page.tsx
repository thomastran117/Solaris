import type { Metadata } from "next";
import AdminCollectionsPage from "@views/admin/AdminCollectionsPage";

export const metadata: Metadata = { title: "Collections" };

export default function Page() {
  return <AdminCollectionsPage />;
}
