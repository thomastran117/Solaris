import type { Metadata } from "next";
import SavedListDetailPage from "@views/SavedListDetailPage";

export const metadata: Metadata = { title: "Saved list" };

export default function Page() {
  return <SavedListDetailPage />;
}
