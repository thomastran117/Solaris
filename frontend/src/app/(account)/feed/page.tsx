import type { Metadata } from "next";
import FeedPage from "@views/FeedPage";

export const metadata: Metadata = { title: "Feed" };

export default function Page() {
  return <FeedPage />;
}
