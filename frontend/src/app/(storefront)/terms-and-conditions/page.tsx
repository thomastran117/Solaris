import type { Metadata } from "next";
import TermsAndConditionPage from "@views/TermsAndConditionPage";

export const metadata: Metadata = { title: "Terms and conditions" };

export default function Page() {
  return (
    <TermsAndConditionPage
      companyName="ShopWave"
      contactEmail="support@shopwave.com"
      lastUpdated="August 22, 2025"
    />
  );
}
