import type { Metadata } from "next";
import AdminFeedbackPage from "@views/admin/AdminFeedbackPage";

export const metadata: Metadata = { title: "Feedback admin" };

export default function Page() {
  return <AdminFeedbackPage />;
}
