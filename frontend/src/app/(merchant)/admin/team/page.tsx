import type { Metadata } from "next";
import AdminTeamPage from "@views/admin/AdminTeamPage";

export const metadata: Metadata = { title: "Team" };

export default function Page() {
  return <AdminTeamPage />;
}
