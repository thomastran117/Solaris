import type { Metadata } from "next";
import AdminProductEditor from "@views/admin/AdminProductEditor";

export const metadata: Metadata = { title: "New product" };

export default function Page() {
  return <AdminProductEditor />;
}
