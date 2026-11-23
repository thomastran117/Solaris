import type { Metadata } from "next";
import SavedListsPage from "@views/SavedListsPage";

export const metadata: Metadata = { title: "My lists" };

export default function Page() {
  return <SavedListsPage />;
}
