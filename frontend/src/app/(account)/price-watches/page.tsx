import type { Metadata } from "next";
import PriceWatchesPage from "@views/PriceWatchesPage";

export const metadata: Metadata = { title: "Price alerts" };

export default function Page() {
  return <PriceWatchesPage />;
}
