import type { Metadata } from "next";
import PrivacyPage from "@views/PrivacyPage";

export const metadata: Metadata = { title: "Privacy policy" };

export default function Page() {
  return (
    <PrivacyPage
      companyName="ShopWave"
      contactEmail="support@shopwave.com"
      dpoEmail="dpo@shopwave.com"
      lastUpdated="August 22, 2025"
    />
  );
}
