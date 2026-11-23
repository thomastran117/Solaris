import type { Metadata } from "next";
import PublicSavedListPage from "@views/PublicSavedListPage";

export const metadata: Metadata = {
  title: "Saved list",
  robots: { index: false },
};

export default function Page() {
  return <PublicSavedListPage />;
}
