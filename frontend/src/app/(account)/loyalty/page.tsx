import type { Metadata } from "next";
import LoyaltyPage from "@views/LoyaltyPage";

export const metadata: Metadata = { title: "Rewards" };

export default function Page() {
  return <LoyaltyPage />;
}
