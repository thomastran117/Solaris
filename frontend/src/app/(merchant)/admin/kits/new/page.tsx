import type { Metadata } from "next";
import AdminKitEditor from "@views/admin/AdminKitEditor";

export const metadata: Metadata = { title: "New kit" };

export default function Page() {
  return <AdminKitEditor />;
}
