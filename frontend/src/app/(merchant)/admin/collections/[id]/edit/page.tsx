import type { Metadata } from "next";
import AdminCollectionEditor from "@views/admin/AdminCollectionEditor";

export const metadata: Metadata = { title: "Edit collection" };

export default function Page() {
  return <AdminCollectionEditor />;
}
