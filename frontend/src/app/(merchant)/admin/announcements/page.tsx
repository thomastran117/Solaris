import type { Metadata } from "next";
import AdminAnnouncementsPage from "@views/admin/AdminAnnouncementsPage";

export const metadata: Metadata = { title: "Announcements" };

export default function Page() {
  return <AdminAnnouncementsPage />;
}
