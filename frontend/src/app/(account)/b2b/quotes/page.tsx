import type { Metadata } from "next";
import B2BQuotesPage from "@views/B2BQuotesPage";

export const metadata: Metadata = { title: "Quotes" };

export default function Page() {
  return <B2BQuotesPage />;
}
