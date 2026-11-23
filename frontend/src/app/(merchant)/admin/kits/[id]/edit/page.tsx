import type { Metadata } from "next";
import AdminKitEditor from "@views/admin/AdminKitEditor";

export const metadata: Metadata = { title: "Edit kit" };

export default function Page() {
  return <AdminKitEditor />;
}
