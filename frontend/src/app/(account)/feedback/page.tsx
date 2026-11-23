import type { Metadata } from "next";
import FeedbackPage from "@views/FeedbackPage";

export const metadata: Metadata = { title: "Feedback" };

export default function Page() {
  return <FeedbackPage />;
}
