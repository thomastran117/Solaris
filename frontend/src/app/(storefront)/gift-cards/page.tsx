import type { Metadata } from "next";
import GiftCardsPage from "@views/GiftCardsPage";

export const metadata: Metadata = { title: "Gift cards" };

export default function Page() {
  return <GiftCardsPage />;
}
